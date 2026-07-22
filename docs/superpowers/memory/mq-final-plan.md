# RocketMQ 最终实施计划

> 本文是 AI 写作可靠后台任务化的落地计划。
>
> 架构动机、替代方案和面试表达见 [mq-deep-dive.md](mq-deep-dive.md)。

---

## 一、目标与范围

### 1.1 目标

将 AI 写作任务从 SSE 触发的本机 `CompletableFuture.runAsync()`，改为由 RocketMQ Consumer 执行的可靠后台作业：

```text
提交任务 -> 事务保存任务和 Outbox -> RocketMQ -> Worker 执行 Agent
                                             |
                                             +-> MySQL 最终状态
                                             +-> Redis Stream 流式事件 -> SSE
```

完成后满足：

- 提交成功后，即使前端不连接 SSE，任务仍会执行。
- Web 接入与 Agent Worker 的执行职责解耦。
- 任务消息可持久排队，支持有限重试和 DLQ。
- 重复消息不会导致两个 Consumer 并发执行同一任务。
- Worker 崩溃造成的超时 `RUNNING` 任务可被回收。
- 保留现有阶段、token、result、done/error 流式体验。
- 页面刷新或断线重连后可查询最终结果，并可按事件 ID 补读短期过程事件。

### 1.2 本期范围

- 仅新增 Topic：`ai-writing-task`。
- 仅改造 AI 写作任务提交、执行、状态、事件推送链路。
- 保留已有 `GET /api/v1/ai-writing/task/{taskId}` 任务详情接口，并扩展排队/运行状态所需字段。
- 保留 SSE 地址 `GET /api/v1/ai-writing/task/stream`，但改为只订阅 `RUNNING` 任务事件。
- `PENDING/RETRYING` 阶段由前端退避轮询，不为排队任务维持 SSE 长连接。
- 保留现有 analyst -> generator -> reviewer -> illustration 的 Agent 编排语义。

### 1.3 非本期范围

- 不新增 `article-event`。
- 不将文章发布、缓存清理、浏览量、点赞量迁入 MQ。
- 不将每个 token 发送到 RocketMQ。
- 不重写 Agent Prompt、Markdown 渲染和记忆抽取业务逻辑。
- 不承诺分布式“恰好一次”模型调用；采用至少一次投递加业务幂等和有限重试。

---

## 二、现状与目标架构

### 2.1 现状

```text
POST /ai-writing/task/submit
  -> 保存 ai_task(PENDING)
  -> 返回 taskId

GET /ai-writing/task/stream
  -> CompletableFuture.runAsync()
  -> ForkJoinPool.commonPool()
  -> generateStream()
  -> SSE 推送 + 更新最终状态
```

### 2.2 目标架构

```text
                         +----------------------------+
                         |  MySQL                     |
提交请求                  |  ai_task(PENDING)          |
    |                    |  outbox_event(NEW)         |
    v                    +-------------+--------------+
AiWritingService                       |
    |                                  | Outbox Publisher
    | 返回 taskId                       v
    +--------------------------> RocketMQ ai-writing-task
                                             |
                                             v
                                   AiTaskConsumer / Worker
                                             |
                           条件更新抢占 PENDING -> RUNNING
                                             |
                                             v
                                    executeTask(taskId)
                                      |             |
                                      v             v
                         MySQL SUCCESS/FAILED   Redis Stream 事件
                                                      |
                                                      v
                                               SSE 订阅与补读
```

### 2.3 职责边界

| 组件 | 最终职责 |
|---|---|
| `AiWritingService.submitTask()` | 限流、草稿校验、构建 Prompt、保存任务和 Outbox |
| Outbox Publisher | 扫描待投递事件并同步发送 RocketMQ；成功后标记已发布 |
| `AiTaskConsumer` | 接收任务命令、原子抢占、调用执行服务、反馈消费结果 |
| `AiWritingService.executeTask()` | 执行 Agent 编排、保存状态和结果、发布流式事件、分类异常 |
| Redis Stream Publisher | 追加阶段、token、结果和终态事件；同时发布轻量实时通知 |
| 任务详情接口 | 返回状态、排队信息和最终结果，供 `PENDING/RETRYING` 前端轮询 |
| SSE Controller/Gateway | 仅校验并订阅 `RUNNING` 任务，补读 Stream、转发实时事件、终态释放连接，不启动任务 |
| Compensation Job | 回收心跳超时的 `RUNNING`，补发需重试任务 |

---

## 三、数据模型与状态机

### 3.1 `ai_task` 扩展

当前任务表保留已有字段：`task_id`、`user_id`、`draft_id`、`task_type`、`prompt_payload`、`enable_illustration`、`response_content`、`status`、`error_msg`、`create_time`、`update_time`。

新增字段：

```sql
ALTER TABLE ai_task
    ADD COLUMN started_at DATETIME NULL COMMENT '本次执行开始时间',
    ADD COLUMN heartbeat_at DATETIME NULL COMMENT 'Worker 执行心跳',
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    ADD COLUMN next_retry_at DATETIME NULL COMMENT '下次允许重试时间',
    ADD COLUMN worker_id VARCHAR(128) NULL COMMENT '执行 Worker 标识';

CREATE INDEX idx_ai_task_recovery
    ON ai_task (status, heartbeat_at);

CREATE INDEX idx_ai_task_retry
    ON ai_task (status, next_retry_at);
```

> 迁移脚本应遵循现有数据库脚本组织方式。上线前确认表名、时间类型和索引命名与现有规范一致。

### 3.2 状态机

新增 `RETRYING` 状态：

```text
PENDING  --Consumer 原子抢占--> RUNNING
RUNNING  --正常完成-----------> SUCCESS
RUNNING  --业务不可恢复错误----> FAILED
RUNNING  --可重试错误----------> RETRYING
RUNNING  --心跳超时------------> RETRYING
RETRYING --Consumer 原子抢占--> RUNNING
```

修改 `AiTaskStatusVO`、`AiTaskEntity`：

- 保留 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`。
- 增加 `RETRYING`。
- 新增 `claim()`、`markRetrying()`、`touchHeartbeat()` 等明确状态方法。
- 禁止普通 `RUNNING` 任务被第二个 Consumer 抢占。

### 3.3 Outbox 表

```sql
CREATE TABLE outbox_event (
    event_id BIGINT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    published_at DATETIME NULL,
    last_error VARCHAR(1024) NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_outbox_publish (status, next_retry_at, create_time)
) COMMENT='本地消息表';
```

建议状态：

```text
NEW -> PUBLISHED
NEW -> RETRYING -> PUBLISHED
NEW/RETRYING -> FAILED
```

第一版事件类型：`AI_WRITING_TASK_CREATED`。

### 3.4 消息契约

Topic：`ai-writing-task`  
Consumer Group：`ai-writing-worker-group`

```json
{
  "taskId": 10001,
  "eventId": "outbox-10001",
  "createdAt": "2026-07-20T13:00:00+08:00"
}
```

约束：

- 消息只携带稳定标识，不携带完整 Prompt。
- Consumer 必须重新从 `ai_task` 读取用户、草稿、任务类型和 Prompt。
- `eventId` 用于日志链路追踪，业务幂等以 `taskId + 条件更新` 为准。

---

## 四、模块改造设计

### 4.1 依赖与配置

修改根 `pom.xml` 或依赖管理位置，增加与项目 Spring Boot/JDK 兼容的 `rocketmq-spring-boot-starter`。先执行依赖兼容性验证，再锁定具体版本。

新增应用配置：

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAMESRV_ADDR:localhost:9876}
  producer:
    group: ai-writing-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 2

ai-writing:
  mq:
    topic: ai-writing-task
    consumer-group: ai-writing-worker-group
    consume-thread-min: 2
    consume-thread-max: 4
    max-reconsume-times: 3
  outbox:
    publish-delay-ms: 1000
    batch-size: 100
  recovery:
    running-timeout-minutes: 5
    max-retry-count: 3
```

最终属性名称按实际 Starter 版本校正。自定义 `ai-writing.*` 属性建议使用 `@ConfigurationProperties` 映射。

### 4.2 专用执行器

在 `ThreadPoolConfig` 增加专用执行器，供 Outbox 发布和补偿任务使用：

```java
@Bean("taskInfrastructureExecutor")
public TaskExecutor taskInfrastructureExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("task-infra-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

不再新增或使用 `aiWritingExecutor` 运行 Agent 主任务。Agent 并发由 RocketMQ Consumer 的消费线程配置控制。

### 4.3 提交任务与 Outbox

修改 `AiWritingService.submitTask()`：

1. 保留当前令牌桶、Redis 提交锁、草稿校验和 Prompt 构建。
2. 同一事务保存 `ai_task(PENDING)` 和 `outbox_event(NEW)`。
3. 返回任务实体，不在此处直接发送 RocketMQ。

伪代码：

```java
@Transactional
public AiTaskEntity submitTask(...) {
    validateRateLimitAndDraft(...);

    AiTaskEntity task = AiTaskEntity.initPending(...);
    aiTaskRepository.save(task);

    OutboxEvent outboxEvent = OutboxEvent.newEvent(
            task.getTaskId(),
            "AI_WRITING_TASK_CREATED",
            aiWritingTopic,
            new AiTaskMessage(task.getTaskId(), eventId, now)
    );
    outboxRepository.save(outboxEvent);
    return task;
}
```

提交锁当前只防止短时间内重复创建。若产品需要“同草稿执行期间不允许再次创建同类型任务”，另行增加基于任务状态的业务校验，不延长 Redisson 锁至分钟级执行过程。

### 4.4 Outbox Publisher

新增 `AiTaskOutboxPublisher`：

1. 固定频率查询 `NEW/RETRYING` 且到达 `next_retry_at` 的事件。
2. 批量领取事件，避免多实例发布器重复扫描；领取应通过条件更新或 `SELECT ... FOR UPDATE SKIP LOCKED` 实现。
3. `rocketMQTemplate.syncSend(topic, payload, timeout)`。
4. `SEND_OK` 后标记 `PUBLISHED`。
5. 发送异常则增加 `retry_count`，计算退避时间，保留错误原因。
6. 超出投递重试阈值后标记 `FAILED` 并告警，不能静默丢弃。

```java
@Scheduled(fixedDelayString = "${ai-writing.outbox.publish-delay-ms}")
public void publishPendingEvents() {
    for (OutboxEvent event : outboxRepository.claimPublishable(batchSize)) {
        try {
            SendResult result = rocketMQTemplate.syncSend(
                    event.getTopic(), event.getPayload(), sendTimeoutMs);
            outboxRepository.markPublished(event.getEventId(), result.getMsgId());
        } catch (Exception e) {
            outboxRepository.scheduleRetry(event.getEventId(), e.getMessage());
        }
    }
}
```

即使 Broker 已收到消息、应用在 `markPublished()` 前退出，事件会再次发送。因此 Consumer 必须按至少一次语义实现。

### 4.5 Consumer 原子抢占

新增 `AiTaskConsumer`，并配置 Topic、Group、消费并发和最大重试次数。

消费步骤：

```text
接收 AiTaskMessage
  -> 条件更新尝试抢占 task
  -> 抢占成功：executeTask(taskId)
  -> 已成功/已失败/仍在健康 RUNNING：正常返回 ACK
  -> 可重试系统异常：抛出，让 MQ 重新消费
```

Repository 新增原子方法：

```sql
UPDATE ai_task
SET status = 'RUNNING',
    started_at = NOW(),
    heartbeat_at = NOW(),
    worker_id = #{workerId},
    update_time = NOW()
WHERE task_id = #{taskId}
  AND status IN ('PENDING', 'RETRYING')
  AND (next_retry_at IS NULL OR next_retry_at <= NOW());
```

只有受影响行数为 `1` 才执行 Agent。

```java
@Override
public void onMessage(AiTaskMessage message) {
    if (!aiTaskRepository.claimTask(message.getTaskId(), workerId)) {
        return;
    }
    aiWritingService.executeTask(message.getTaskId());
}
```

不要采用“先查状态，再决定是否执行”的写法。两个 Consumer 可能同时读到 `PENDING`，导致重复模型调用。

### 4.6 执行服务重构

将当前 `generateStream(taskId, userId, Consumer<...>)` 拆分：

```text
executeTask(taskId)                 Consumer 调用，执行 Agent 和写最终状态
AgentWritingRunner.run(task, sink)  复用 analyst/generator/reviewer/illustration 编排
TaskEventPublisher.publish(...)     发布阶段、token、终态事件
subscribeTaskEvents(...)            SSE 调用，只读取事件
```

`executeTask()` 的职责：

```java
public void executeTask(Long taskId) {
    AiTaskEntity task = aiTaskRepository.queryById(taskId);
    try {
        String content = agentWritingRunner.run(task, event -> {
            aiTaskRepository.touchHeartbeat(taskId);
            taskEventPublisher.publish(taskId, event);
        });

        aiTaskRepository.markSuccess(taskId, content);
        taskEventPublisher.publish(taskId, TaskStreamEvent.done());
    } catch (RetryableAgentException e) {
        aiTaskRepository.markRetrying(taskId, safeMessage(e));
        throw e;
    } catch (Exception e) {
        aiTaskRepository.markFailed(taskId, safeMessage(e));
        taskEventPublisher.publish(taskId, TaskStreamEvent.error(safeMessage(e)));
    }
}
```

要求：

- 复用现有 Markdown 格式化、配图和记忆抽取逻辑。
- 不向执行服务传入 `ResponseBodyEmitter` 或 HTTP 回调。
- 可重试异常必须重新抛出，否则 RocketMQ 会认为消费成功。
- 不可重试业务错误写 `FAILED` 后正常返回。
- 心跳不必每个 token 落库，可按固定间隔节流更新，避免写放大。

### 4.7 异常与重试策略

| 分类 | 例子 | 任务状态 | Consumer 行为 |
|---|---|---|---|
| 可重试 | 模型 API 连接超时、临时 5xx、短暂限流 | `RETRYING` | 重新抛出，触发有限次数 MQ 重试 |
| 不可重试 | 参数非法、任务不存在、草稿权限错误 | `FAILED` | 正常返回 ACK |
| 未知异常 | 代码错误、解析异常 | 先 `RETRYING`，超过阈值后 `FAILED` | 有限重试，最终 DLQ |

默认重试次数不是业务策略。AI 调用有成本和不确定性，建议 `max-reconsume-times` 配置为 2 到 3，并与 `retry_count` 上限保持一致。

### 4.8 Redis Stream 与 SSE

新增 `TaskEventPublisher`，按任务建立 Stream：

```text
ai:task:stream:{taskId}
```

事件字段：

```json
{
  "phase": "generating",
  "type": "token",
  "content": "消息队列可以...",
  "timestamp": "172..."
}
```

事件类型复用现有语义：`status`、`token`、`result`、`done`、`error`。

SSE 改造原则：

1. 校验 `taskId` 归属。
2. 从 `Last-Event-ID` 后补读已存在事件。
3. 等待新事件并推送。
4. 收到 `done/error` 事件后完成 emitter。
5. 不再调用 `runAsync()` 或 `generateStream()`。

前端提交后先轮询；仅发现 `RUNNING` 时建立 SSE：

```javascript
const { taskId } = await submitTask(request);
waitUntilRunning(taskId); // RUNNING 后才调用 connectTaskStream(taskId)
```

实现前确认浏览器 `EventSource` 对自定义 `Last-Event-ID` 的支持方式。原生 `EventSource` 会在同一连接的自动重连中携带最后事件 ID，但首次手动恢复通常不能自定义 Header；本项目第一版使用 `lastEventId` 查询参数，或改用支持 Header 的 fetch-stream 客户端。

设置 Stream 保留策略，例如任务终态后保留 24 小时，再通过定时清理或 `XTRIM` 删除；MySQL 仍是最终结果的唯一事实来源。

### 4.9 补偿任务

新增 `AiTaskRecoveryJob`：

1. 扫描 `RUNNING` 且 `heartbeat_at` 超过阈值的任务。
2. 条件更新为 `RETRYING`，避免多个 Job 并发重复回收。
3. 创建新的 Outbox 事件用于重新投递。
4. 超过最大重试数则 `FAILED`，记录超时原因并告警。

```sql
UPDATE ai_task
SET status = 'RETRYING',
    retry_count = retry_count + 1,
    next_retry_at = NOW(),
    error_msg = 'worker heartbeat timeout'
WHERE task_id = #{taskId}
  AND status = 'RUNNING'
  AND heartbeat_at < DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE);
```

恢复任务不能只修改状态，必须与新 Outbox 事件在同一事务中创建，否则会再次产生“任务可重试但无人调度”的问题。

---

## 五、SSE 生命周期、容量与事件分发设计

### 5.1 连接生命周期

SSE 不是“提交任务后立即永久建立”的通道。它只服务于真正开始生成的任务：

```text
前端 POST submit
  -> 返回 taskId，状态 PENDING
  -> 前端轮询 GET /task/{taskId}

PENDING / RETRYING
  -> 仅显示排队或重试状态
  -> 不建立 SSE

Consumer 抢占成功
  -> MySQL 状态变为 RUNNING
  -> Worker 发布 status: running 事件
  -> 前端下一次轮询发现 RUNNING
  -> 建立 GET /task/stream?taskId=xxx

RUNNING
  -> SSE 接收 status / token / result / done / error

SUCCESS / FAILED
  -> 推送 done / error
  -> SSE complete
  -> 前端改读 MySQL 最终结果，不再重连 SSE
```

这条规则将长期 SSE 连接数从“所有已提交任务数”收敛为“正在生成且用户选择实时观看的任务数”。例如 Consumer 最大并发为 20，则正常情况下同一时刻只有约 20 个任务产生 token；其余排队任务不应占用长连接。

### 5.2 前端状态机与轮询策略

```text
submit -> PENDING
PENDING/RETRYING -> 退避轮询 task detail
RUNNING -> connectSse(taskId)
SSE done/error -> close SSE -> query final detail
SSE network error -> query task detail
  -> RUNNING: 带 lastEventId 重连一次
  -> PENDING/RETRYING: 回退轮询
  -> SUCCESS/FAILED: 渲染最终结果并停止
```

轮询建议采用退避并加入随机抖动，避免大量用户整秒打到数据库：

| 阶段 | 建议间隔 | 目的 |
|---|---:|---|
| 首次提交后 30 秒内 | 2 秒 + 0~500ms 抖动 | 尽快感知任务开始 |
| 排队 30 秒到 2 分钟 | 5 秒 + 抖动 | 降低数据库压力 |
| 排队超过 2 分钟 | 10 秒 + 抖动 | 任务积压时保护 API 与 DB |
| SSE 断线重连 | 1 次即时状态查询，再按状态决定 | 避免无限快速重连 |

任务详情 DTO 增加可选字段：`queueStatus`、`retryCount`、`startedAt`、`estimatedWaitSeconds`。第一版无法可靠计算预计等待时间时返回空值，不伪造排队位置。

前端伪代码：

```javascript
async function waitUntilRunning(taskId) {
  let delay = 2000;
  while (true) {
    const task = await queryTask(taskId);
    if (task.status === 'RUNNING') {
      connectTaskStream(taskId);
      return;
    }
    if (task.status === 'SUCCESS' || task.status === 'FAILED') {
      renderFinalTask(task);
      return;
    }
    await sleep(delay + Math.random() * 500);
    delay = Math.min(delay < 5000 ? 5000 : 10000, 10000);
  }
}
```

### 5.3 SSE 接口契约

保留：

```text
GET /api/v1/ai-writing/task/stream?taskId={taskId}&lastEventId={id}
Accept: text/event-stream
```

服务端行为：

1. 从 MySQL 查询任务并校验 `userId` 归属。
2. 若任务为 `PENDING/RETRYING`，返回 `409 Conflict` 与当前任务状态，不创建长连接。
3. 若任务为 `SUCCESS/FAILED`，返回终态事件后关闭，或让前端改用任务详情接口读取最终结果。
4. 若任务为 `RUNNING`，先从 Redis Stream 补读 `lastEventId` 之后的事件，再订阅新事件。
5. 同一 `userId + taskId` 只能保留一条活跃连接；新连接建立时关闭旧连接，或返回 `409`。第一版选择关闭旧连接，便于刷新页面恢复。
6. `done/error`、超时、客户端断开和发送失败时必须取消订阅、删除连接注册表记录并完成 emitter。

SSE 事件使用标准字段：

```text
id: 1720000000000-0
event: token
data: {"phase":"generating","type":"token","content":"..."}

```

`id` 使用 Redis Stream Record ID，使前端能在重连时传回 `lastEventId`。当前 `ResponseBodyEmitter.send("data: ...")` 的手写协议需要改为构造正确的 `id/event/data` SSE 帧，或迁移到 Spring 的 `SseEmitter`/WebFlux `ServerSentEvent`。

### 5.4 实时分发与断线补读

Redis Stream 不能以“每条 SSE 开一个阻塞 XREAD 线程”的方式使用。那会导致 10,000 条 SSE 对应 10,000 个阻塞读取和大量线程/连接。

采用双通道：

```text
Worker
  -> Redis Stream XADD：持久短期事件，供补读
  -> Redis Pub/Sub PUBLISH：实时通知 SSE Gateway

SSE Gateway
  -> 共享订阅 task-event-live 频道
  -> 根据 taskId 在本机连接注册表定位活跃连接
  -> 直接推送给对应 emitter

浏览器重连
  -> XREAD Redis Stream，补读 lastEventId 之后的事件
  -> 补读完成后继续接收 Pub/Sub 实时事件
```

说明：

- Stream 是“回放日志”，终态后保留 24 小时并定时清理。
- Pub/Sub 是“在线通知”，不承担可靠性；断线漏掉的事件由 Stream 补读。
- 每个任务通常只对应一个用户和一条 SSE，不需要为 token 建立 RocketMQ Topic。
- 初期单实例时仍按该接口抽象实现；多实例部署时只需让每个 SSE 实例共享 Pub/Sub 频道即可。

### 5.5 连接上限与降级

SSE 连接数独立于 MQ Consumer 并发：Consumer 控制同时执行多少 Agent，SSE 控制同时有多少浏览器在观看任务。必须配置单独的 SSE 保护策略：

| 维度 | 第一版建议 | 达到阈值后的行为 |
|---|---:|---|
| 同一 `userId + taskId` | 1 条 | 关闭旧连接，保留最新连接 |
| 单用户活跃 SSE | 2 条 | 新连接返回 `429`，前端改轮询 |
| 单实例 SSE | 按压测确定，例如 1,000 条 | 拒绝新订阅并返回降级标记 |
| 全局 SSE | 由网关和实例数容量决定 | 扩容 SSE Gateway，或仅允许高价值长文本流式 |

当连接达到容量阈值时，后台 MQ/Worker 必须继续执行任务；仅前端体验降级：

```text
SSE 容量正常 -> RUNNING 任务可订阅 token
SSE 容量不足 -> 返回 SSE_CAPACITY_EXCEEDED
                -> 前端轮询 task detail
                -> 任务继续在 Worker 中执行
```

### 5.6 部署演进

第一版可让现有 Web 应用承载 SSE，但必须配置连接数、文件描述符和代理超时，并通过压测确定安全上限。

当需要支撑数千到上万连接时，拆分部署：

```text
API 实例        登录、提交、查询
Worker 实例     RocketMQ Consumer、模型调用
SSE Gateway     长连接、Redis Pub/Sub、Stream 补读
```

SSE Gateway 建议采用 Spring WebFlux + Reactor Netty 等非阻塞模型；不建议将 10,000 条长连接建立在“一连接一阻塞读取/工作线程”的实现上。

负载均衡与运行时设置：

- 增大 `ulimit -n`，预留 socket、Redis、日志等文件描述符余量。
- 调整网关/LB idle timeout，SSE 每 15 到 30 秒发送 `heartbeat` 注释帧防止空闲断开。
- 关闭代理响应缓冲，确保 token 及时送达。
- 按活跃连接数进行扩容和负载均衡，不只按 HTTP QPS。
- 监控连接数、断线率、平均连接时长、事件发送延迟和重连率。

### 5.7 本期实施选择

本期按以下优先级落地：

1. **必须**：排队轮询，`RUNNING` 后建 SSE，终态立即释放。
2. **必须**：任务状态查询、连接去重、断线清理、心跳、容量阈值降级。
3. **必须**：Redis Stream 支持 `lastEventId` 补读。
4. **建议**：Redis Pub/Sub 实时分发抽象；单实例可先直接通知本机连接注册表，多实例前必须切换为 Pub/Sub。
5. **后续规模化**：独立 WebFlux SSE Gateway 和连接数水平扩容。

---

## 六、文件与模块清单

| 位置 | 改动 |
|---|---|
| 根 `pom.xml` | 新增 RocketMQ Starter 与版本管理 |
| `sutone-agent-bok-app/.../ThreadPoolConfig.java` | 新增 `taskInfrastructureExecutor` |
| `sutone-agent-bok-app/.../application-*.yml` | RocketMQ、Outbox、消费并发、恢复配置 |
| `docs/dev-ops/docker-compose-environment.yml` | NameServer、Broker、持久卷、健康检查 |
| 数据库迁移目录 | 扩展 `ai_task`，新建 `outbox_event` |
| infrastructure DAO/Repository | `AiTask` 条件抢占、心跳、重试；Outbox CRUD 与事件领取 |
| infrastructure `mq` 包 | Topic 配置、消息 DTO、Producer、Consumer |
| domain `ai_writing` | 拆分 `executeTask()`、Agent Runner、事件发布接口 |
| infrastructure Redis 包 | Redis Stream Publisher/Subscriber |
| trigger `AiWritingController` | SSE 改为订阅模式，删除任务启动逻辑 |
| app Job/Task 包 | Outbox Publisher 与任务恢复补偿 Job |
| 测试目录 | 单元、集成、故障恢复与端到端验证 |

新包结构遵循项目既有“父包接口 + 子包实现”模式，例如：

```text
infrastructure/mq/
  config/
  producer/
  consumer/
  model/

domain/agent/service/
  ITaskEventPublisher.java
  ai_writing/
    AgentWritingRunner.java
```

具体包名在实施前对照现有模块结构确认，不跨越已有领域边界。

---

## 七、实施顺序

### 阶段 A：基础准备

1. 核对 Spring Boot、JDK、RocketMQ Starter 兼容版本。
2. 在本地 Docker Compose 启动 NameServer 和 Broker。
3. 增加应用配置和连接健康检查。
4. 添加 `taskInfrastructureExecutor`。
5. 为 `ai_task` 和 `outbox_event` 编写数据库迁移与回滚脚本。

验收：应用可连接 NameServer；迁移可重复执行；现有功能和测试不受影响。

### 阶段 B：可靠投递

1. 实现 Outbox 实体、DAO/Repository、状态流转。
2. 将 `submitTask()` 改为同事务保存任务和 Outbox。
3. 实现 Outbox Publisher 和发送失败退避。
4. 添加日志字段：`taskId`、`eventId`、RocketMQ `msgId`。

验收：Broker 短暂不可用时，提交仍成功且 Outbox 保留；Broker 恢复后消息最终投递。

### 阶段 C：消费与状态机

1. 新增 `RETRYING` 状态及任务扩展字段。
2. 实现条件更新的原子抢占。
3. 实现 `AiTaskConsumer` 和消费并发配置。
4. 重构 `generateStream()` 为 `executeTask()` 与 Agent Runner。
5. 实现异常分类和有限重试。

验收：重复消息、两个 Consumer 并发消费时，只有一个执行 Agent；不可重试错误直接失败；可重试错误进入有限重试。

### 阶段 D：流式输出解耦与连接治理

1. 实现 Redis Stream 事件模型、保留策略和 `lastEventId` 补读。
2. 实现 Redis Pub/Sub 实时通知抽象；单实例可先使用本机连接注册表，多实例部署前必须启用共享通知。
3. 重构 SSE 为仅订阅 `RUNNING` 任务的模式，删除 `CompletableFuture.runAsync()` 任务执行入口。
4. 前端实现 `PENDING/RETRYING` 的退避轮询，并在发现 `RUNNING` 后才建立 SSE。
5. 实现连接去重、单用户上限、heartbeat、断线清理和 SSE 容量阈值降级。
6. 前端验证实时 token、终态、断线重连、排队轮询和容量降级。

验收：排队任务不保持 SSE；前端不连接 SSE 时任务仍完成；RUNNING 任务可实时展示；重新连接后可补读保留事件；达到连接阈值时前端自动回退轮询；最终结果可由任务详情接口查询。

### 阶段 E：故障恢复与观测

1. 实现 `RUNNING` 心跳、超时回收和补发 Outbox。
2. 配置 Consumer 重试上限和 DLQ。
3. 增加指标、告警和结构化日志。
4. 执行 Broker、Worker、网络故障演练。

验收：Worker 在 Agent 执行中退出后，任务不会永久 `RUNNING`；重试超限可在 DLQ 定位；积压可观测。

---

## 八、测试与验收清单

### 8.1 单元测试

- `AiTaskEntity`：状态机、非法状态转换、重试计数。
- `AiWritingService`：提交时同时保存任务和 Outbox。
- Outbox Publisher：成功标记、发送异常退避、超限失败。
- Consumer：抢占成功执行，抢占失败不执行。
- `executeTask()`：可重试异常重新抛出，不可重试异常落 `FAILED`。
- 事件发布：阶段、token、终态事件序列。

### 8.2 集成测试

- 使用 Testcontainers 或独立 Docker RocketMQ 验证 Producer/Consumer。
- DB 事务回滚时任务和 Outbox 不产生脏记录。
- Broker 不可用时 Outbox 后续可补发。
- 同一 `taskId` 发送两次，Agent Runner 只被调用一次。
- 两个 Consumer 同时竞争同一任务，只有一个条件更新成功。
- Worker 退出后任务被补偿器回收并重新投递。
- 超过重试上限进入 DLQ。
- `PENDING/RETRYING` 任务请求 SSE 时被拒绝，前端继续退避轮询。
- SSE 从指定事件 ID 补读 Redis Stream。
- 同一 `userId + taskId` 重复建立 SSE 时，旧连接被关闭且订阅资源释放。
- SSE 达到容量阈值时返回降级标记，前端改为轮询，Worker 不受影响。

### 8.3 手动演练

1. 提交任务后不打开 SSE，确认最终仍是 `SUCCESS/FAILED`。
2. 提交后任务保持 `PENDING` 时，确认前端只轮询且 SSE 请求返回当前状态而不保持连接。
3. 任务变为 `RUNNING` 后，确认前端建立 SSE 并实时收到 token。
4. 生成中关闭浏览器并重新连接，确认可读取终态和保留 token。
5. 制造同一任务重复订阅，确认旧 SSE 关闭并释放资源。
6. 触发 SSE 容量阈值，确认新订阅降级为轮询而 Worker 继续执行。
7. 生成中停止 Worker，等待心跳超时，确认任务变为 `RETRYING` 并重新调度。
8. 停止 Broker 后提交任务，确认 Outbox 累积；恢复 Broker 后确认投递。
9. 人为制造模型网络超时，确认有限重试；人为制造参数错误，确认直接失败。
10. 制造堆积，验证 Consumer 并发、队列等待时间和模型配额保护。

---

## 九、可观测性与运维

### 9.1 必备指标

- Outbox：`NEW/RETRYING/FAILED` 数量、最老未投递事件年龄、发送失败次数。
- MQ：Topic 堆积量、消费者延迟、DLQ 数量、发送耗时和消费耗时。
- 任务：`PENDING/RUNNING/RETRYING/SUCCESS/FAILED` 数量、平均执行时长、超时任务数。
- 模型：请求数、错误率、限流次数、并发数、单任务 token/耗时。
- Redis Stream：每任务事件数、保留长度、清理次数、SSE 重连数。

### 9.2 告警建议

- Outbox 最老待发送事件超过 1 分钟。
- `RUNNING` 任务心跳超时。
- DLQ 出现消息。
- 消费积压持续增长。
- 模型 API 连续超时或限流。
- 单用户待处理任务数超过阈值。

### 9.3 容量原则

- Consumer 并发先受模型 API 配额约束，再考虑 CPU/内存。
- 不因积压盲目提高 Consumer 线程数。
- 单 Broker 仅用于开发验证；生产场景需要持久卷、刷盘策略、主从复制、监控和故障演练。
- Redis Stream 只保存短期过程事件，最终结果始终以 MySQL 为准。

---

## 十、完成定义

满足以下条件才认为 MQ 改造完成：

- [ ] `submitTask()` 不再直接启动 Agent，仅保存任务和 Outbox。
- [ ] SSE Controller 不再使用 `CompletableFuture.runAsync()` 启动任务。
- [ ] Outbox 与 `ai_task` 同事务保存，并能在 Broker 恢复后补发。
- [ ] Consumer 使用数据库条件更新原子抢占任务。
- [ ] Agent 任务可在用户离线后完成。
- [ ] `RUNNING` 超时任务可以回收、重试或失败告警。
- [ ] 可重试与不可重试异常有明确处理。
- [ ] 终态、DLQ、Outbox 和任务积压可观测。
- [ ] 现有完整测试通过，新增单元与集成测试覆盖关键故障路径。
- [ ] 重复消费、Worker 崩溃、Broker 不可用、SSE 断线重连均完成演练。
