# RocketMQ 深度解析

> 本文带你从现有代码出发，一步步理解为什么要引入消息队列、每个设计决策背后的原因。
>
> 具体表结构、类职责、代码改造详见 [mq-final-plan.md](mq-final-plan.md)。

---

## 阅读指引

这篇文章是**从你的现有代码出发**写的。每一节都先亮出你项目中已经存在的代码/流程，再分析问题，最后讲怎么改。

如果你是第一次接触 MQ 或分布式系统，建议**按顺序读**。前几节把核心概念讲透之后，后面的设计决策就自然理解了。

文中用 📦 标记"生活比喻"，用 ❓ 标记"面试常见追问"。

---

## 一、你的代码现在是怎么跑的

### 1.1 一条请求的完整旅程

用户在编辑器里点"AI 写作"，前端发两个请求：

**第一步：提交任务**

```
POST /api/v1/ai-writing/task/submit
  → AiWritingController.submitTask()          # trigger/http/AiWritingController.java:61
  → AiWritingService.submitTask()             # domain/.../ai_writing/AiWritingService.java:85
      1. 检查速率限制 (Redis 令牌桶, 每人每分钟5次)
      2. 加分布式锁 (防止同一草稿重复提交)
      3. 校验草稿是否存在
      4. 查询记忆上下文，拼装 Prompt
      5. INSERT 一条 ai_task 记录, status = PENDING (code=3)
      6. 释放锁，返回 taskId
```

到现在为止，任务只是**存进了数据库**，状态是 PENDING。还没有开始执行。

**第二步：打开 SSE 连接看生成过程**

```
GET /api/v1/ai-writing/task/stream?taskId=xxx
  → AiWritingController.stream()              # trigger/http/AiWritingController.java:135
      1. 创建一个 ResponseBodyEmitter (10分钟超时)
      2. 调用 CompletableFuture.runAsync(() -> {
             aiWritingService.generateStream(taskId, userId, eventConsumer)
         })
      3. return emitter (HTTP 连接保持打开)
```

注意这里的关键代码：

```java
// AiWritingController.java 第 150 行
CompletableFuture.runAsync(() -> {
    aiWritingService.generateStream(taskId, currentUserId, event -> {
        sendEvent(emitter, toStreamDTO(event));  // 把 token 推给浏览器
    });
});
```

`runAsync()` 没指定线程池，所以任务跑在 `ForkJoinPool.commonPool()` 里——这个池子是 JVM 全局共享的，所有没指定线程池的异步任务都往里扔。

**generateStream 里发生了什么：**

```
AiWritingService.generateStream()              # domain/.../ai_writing/AiWritingService.java:126
  1. 从 DB 查出 task, 校验归属
  2. PENDING → RUNNING (status 从 3 变成 0)
  3. 调用 Google ADK, 创建 Agent 会话
  4. chatService.handleMessageStream().blockingForEach(event -> {
       analyst 阶段 → 跳过 (只做分析, 不输出)
       generator 阶段 → 每个 token 推给 eventConsumer (SSE)
       reviewer 阶段 → 逐行累积, 渲染 Markdown, 推给 SSE
     })
  5. 配图阶段 (如果开启了 enableIllustration):
     analyzeIllustrations() → agent 300003 分析哪些段落需要配图
     generateIllustration() → agent 300000 生成 draw.io XML
     injectIllustration()   → 把配图插入正文
  6. formatMarkdown() → CommonMark 规范化
  7. markSuccess() → DB status 变为 SUCCESS, 保存完整文章
  8. memoryManager.addAsync() → 异步抽取用户记忆
  9. 发送 result 和 done 事件 → emitter.complete()
```

📦 **生活比喻——快递驿站取件模式**

你现在的架构就像一个"驿站取件"模式:

> 用户在网上下单（POST submit）→ 订单生成但**没人送货**→ 用户必须亲自去驿站盯着（打开 SSE 连接）→ 驿站工作人员才开始打包（runAsync 启动生成）→ 一边打包一边从窗口递出来（SSE 推送 token）→ 用户走了窗口就关了（连接断开=任务丢了）

---

### 1.2 这段代码存在 5 个问题

把代码放在面前，我们一个一个看。

**问题 1：ForkJoinPool 是公共资源，不受你控制**

```java
// AiWritingController.java:150
CompletableFuture.runAsync(() -> { ... });  // 没有指定线程池！
```

`ForkJoinPool.commonPool()` 的线程数默认等于 CPU 核数 - 1。你的 Mac 可能是 10 核，那就只有 9 个线程。但你的 `ThreadPoolConfig` 里明明配了一个 200 线程的池子（`sutone-agent-bok-app/.../ThreadPoolConfig.java`），这里却没用到。

更关键的是，一旦进入 `generateStream()`，`blockingForEach()` 会**一直占用这个线程**直到生成完成。一个 5 分钟的写作任务就把一个 ForkJoinPool 线程占了 5 分钟。

**问题 2：任务启动依赖前端连 SSE**

看你 `submitTask()` 的代码（第 85 行）：它只做了 `aiTaskRepository.save(task)`，状态是 PENDING。任务的真正执行在 `generateStream()` 里（第 129 行 `task.startRunning()`）。

这意味着：**如果用户提交后关掉了浏览器，任务永远是 PENDING，永远不会执行。**

📦 **比喻**：你网上下了个外卖单，但骑手要等你亲自到店门口才出发。你不到店，菜永远不会开始做。

**问题 3：进程崩溃 = 正在执行的任务永远卡住**

看 `generateStream()` 第 129 行：`task.startRunning()` 把状态改成了 RUNNING。如果这时候你的应用挂了（OOM、kill -9、机房断电），数据库里就留下一条 `status = RUNNING` 但永远没人管的记录。

你的代码里没有心跳机制、没有超时检测、没有补偿逻辑。这条记录就烂在数据库里了。

**问题 4：Web 服务和 Agent 执行绑在同一台机器上**

现在你的 `AiWritingController`（处理 HTTP 请求）和 `generateStream()`（跑 Agent 模型调用）在同一个 JVM 进程里。这意味着：

- 你不能单独扩容 Agent 执行能力（比如加两台机器专门跑模型调用）
- 一个慢的 Agent 任务不会影响其他 HTTP 请求……但仅限线程池还够用的时候
- 模型调用的并发数 = 用户同时打开 SSE 并发数，你没法主动限制

**问题 5：断线后无法补读 token**

SSE 是基于 HTTP 的单向流。如果用户网络断了 5 秒又连上，这 5 秒的 token 就丢了。你的代码里没有任何"断线续读"机制——`sendEvent()` 只是 try-catch 了 IOException，丢了就丢了。

---

## 二、消息队列到底解决了什么

### 2.1 一句话讲清楚 MQ 的角色

📦 **生活比喻——餐厅后厨**

不用 MQ（现在）：
> 顾客点菜 → 服务员（Controller）把单子放桌上 → 等厨师（ForkJoinPool 线程）空了去拿 → 顾客走了单子就扔了

用 MQ（改造后）：
> 顾客点菜 → 服务员把单子挂到厨房的**订单轨道**上 → 厨师按自己的节奏从轨道取单 → 做好了喊服务员上菜（SSE 推送）→ 顾客中途去洗手间回来也能看到之前上的菜（Redis Stream 补读）

这个"订单轨道"就是 RocketMQ 扮演的角色。它做了三件事：

1. **接收订单**：任务提交后立刻进入队列，不受 Web 服务是否繁忙影响
2. **持久保存**：单子挂在轨道上不会丢，厨师（Worker）崩溃重启后单子还在
3. **按能力取单**：厨师一次拿一个，做完了再拿下一个，不会同时做 100 个菜导致厨房爆炸

在技术层面：

> **MySQL 记录"任务是什么、最终结果是什么"**  
> **RocketMQ 负责"哪些任务等待执行"**  
> **SSE 负责"用户此刻看到什么"**

### 2.2 为什么不是加个线程池就能解决

你可能想：问题 1 不就是 `runAsync()` 没指定线程池吗？我传一个自己的池子进去不就行了？

```java
// 这样不就解决了吗？
CompletableFuture.runAsync(() -> { ... }, myOwnThreadPool);
```

这确实能解决**问题 1**（隔离公共线程池）。但解决不了：

| 问题 | 专用线程池能解决吗 | 为什么 |
|------|:---:|------|
| ForkJoinPool 被占满 | ✅ 能 | 自己的池子不影响公共池 |
| 用户离线任务不执行 | ❌ 不能 | 任务还是靠前端连 SSE 才启动 |
| 进程重启丢任务 | ❌ 不能 | 线程池在内存里，重启就没了 |
| Web 和 Worker 不能分开扩容 | ❌ 不能 | 还是在同一个 JVM 里 |
| 断线补读 token | ❌ 不能 | 跟线程无关，需要存储 |

所以 MQ 不是为了"线程池不够用"，而是为了让 AI 写作任务变成一个**真正的后台作业**——像发邮件、导出报表、视频转码那样，提交了就执行，不用人守着。

📦 **比喻**：加线程池 = 你从"用一个公共灶台"变成了"用你自己的灶台"。但灶台还是在同一间厨房里，火灾（进程崩溃）照样烧，你也不能把灶台搬到隔壁房间（独立扩容 Worker）。

---

## 三、改造后的架构全景图

### 3.1 改造前 vs 改造后

```
【改造前】
POST /submit → 存 PENDING → 返回 taskId
                                ↓
GET /stream  → CompletableFuture.runAsync() → generateStream()
                 ↑                                    ↑
             ForkJoinPool                      blockingForEach()
             公共线程池                         一个线程占用几分钟
```

```
【改造后】
POST /submit → 同一事务: 存 PENDING + 存 outbox_event
                                ↓
                 Outbox Publisher (定时扫描, 发到 RocketMQ)
                                ↓
                          RocketMQ Topic
                          ai-writing-task
                                ↓
                 AiTaskConsumer (原子抢占任务)
                                ↓
                      executeTask(taskId)
                       /              \
                      v                v
              MySQL SUCCESS/FAILED   Redis Stream 事件
                                        ↓
                                   SSE (仅 RUNNING 任务)
```

### 3.2 新架构下一条请求的旅程

```
1. 用户提交任务
   POST /submit → AiWritingService.submitTask()
     @Transactional  ← 新增：事务保证 task + outbox 同时落库
     ├─ aiTaskRepository.save(task)        # PENDING
     └─ outboxRepository.save(outboxEvent)  # NEW
   返回 taskId, 前端开始轮询

2. Outbox 投递 (后台定时任务)
   @Scheduled(每1~3秒) → OutboxPublisher
     扫描 outbox_event WHERE status IN ('NEW','RETRYING')
     → rocketMQTemplate.syncSend(topic, message)
     → 标记 PUBLISHED

3. Worker 领取任务
   AiTaskConsumer.onMessage(message)
     → UPDATE ai_task SET status='RUNNING', worker_id=...
       WHERE task_id=? AND status IN ('PENDING','RETRYING')
       只有 affectedRows == 1 的 Worker 才能执行
     → aiWritingService.executeTask(taskId)

4. 执行 + 推送事件
   executeTask()
     → Agent 编排 (analyst → generator → reviewer → illustration)
     → 每生成一个 token → Redis Stream XADD (持久短期)
                       → Redis Pub/Sub PUBLISH (实时通知 SSE)
     → 成功: MySQL SUCCESS + done 事件
     → 失败(可重试): MySQL RETRYING + 重新投递
     → 失败(不可重试): MySQL FAILED + 停止

5. 前端展示
   提交后: 轮询 GET /task/{taskId}
   发现 RUNNING: 建立 SSE GET /task/stream
   收到 done/error: 关闭 SSE, 从 task detail 读最终结果
```

---

## 四、核心概念逐一说透

以下每个概念都按"场景 → 问题 → 解决方案"的节奏展开。

### 4.1 Transactional Outbox —— 保证"下单"和"通知厨房"一定同时成功

**场景**：你（Controller）收到用户的 AI 写作请求，做了两件事：
1. 往数据库里插一条 `ai_task` 记录
2. 往 RocketMQ 发一条消息，告诉 Worker "有个新任务"

**问题**：如果步骤 1 成功、步骤 2 失败（网络抖动/RocketMQ 挂了），数据库里有记录但没有消息——任务永远 PENDING。

反过来，步骤 2 成功、步骤 1 失败（比如数据库突然不可用），消息发出去了但 Worker 去数据库里找不到这条任务。

这是分布式系统里的经典问题——**两个系统的操作没法天然原子化**。

📦 **比喻**：你在餐厅点菜，收银员一边收钱一边朝厨房喊"一份宫保鸡丁"。如果收到一半嗓子哑了喊不出来——钱收了，菜没做。

**解决方案——Transactional Outbox（事务发件箱）**：

不在业务代码里直接发 MQ 消息，而是**在同一个数据库事务里**，往一张 `outbox_event` 表多插一条记录：

```java
@Transactional  // 数据库事务保证下面两个 INSERT 要么都成功, 要么都失败
public AiTaskEntity submitTask(...) {
    // 1. 保存任务 (原来的逻辑)
    aiTaskRepository.save(task);           // INSERT INTO ai_task ...

    // 2. 同时保存"待发送事件" (新增)
    OutboxEvent event = OutboxEvent.newEvent(
        task.getTaskId(),
        "AI_WRITING_TASK_CREATED",        // 事件类型
        "ai-writing-task",                 // 目标 Topic
        new AiTaskMessage(task.getTaskId())
    );
    outboxRepository.save(event);         // INSERT INTO outbox_event ...

    return task;
}
```

因为两个 INSERT 在**同一个数据库事务**里，它们要么都成功，要么都失败。不存在"一个成功一个失败"的窗口。

然后，一个后台定时任务（Outbox Publisher）去扫描 `outbox_event` 表里 `status = 'NEW'` 的记录，把它们发到 RocketMQ，发成功后标记为 `PUBLISHED`。

```
数据库事务保证了:
  ai_task 存在 ←→ outbox_event 一定存在
  ai_task 不存在 ←→ outbox_event 一定不存在

Outbox Publisher 保证:
  outbox_event 最终一定会被发到 RocketMQ
  (即使重试几次, 最终要么 PUBLISHED 要么 FAILED 并告警)
```

❓ **面试追问："那 Outbox Publisher 发消息时挂了怎么办？"**

> Publisher 发消息成功后、标记 PUBLISHED 之前挂了 → 消息重复投递。所以 Consumer 必须支持幂等（见 4.2）。Publisher 发消息失败 → event 状态变为 RETRYING，下次继续扫。

### 4.2 幂等消费 + 原子抢占 —— 同一个任务只执行一次

**场景**：因为网络超时、Publisher 重试、或 RocketMQ 自身的重投递机制，同一条任务消息可能被投递多次。

**问题**：如果两个 Worker（或同一个 Worker 的两次消费）同时拿到 `taskId = 10001`，都去调 `generateStream()`，就会：
- 两个模型调用同时跑，浪费 token 额度
- 生成两篇文章，不知道哪个是最终结果

📦 **比喻**：外卖平台把同一个订单推给了两个骑手。两个骑手都去店里取餐，店里做了两份。

**你可能会想这样写：**

```java
// ❌ 错误写法 —— 存在竞态条件
public void onMessage(AiTaskMessage message) {
    AiTaskEntity task = aiTaskRepository.queryById(message.getTaskId());
    if (task.getStatus() == PENDING) {           // ← 检查
        task.startRunning();
        aiTaskRepository.update(task);            // ← 更新
        executeTask(task);                        // ← 执行
    }
}
```

问题在哪？两个 Worker 可以**同时**读到 `status == PENDING`（都在另一个 Worker 的 UPDATE 生效之前读的）。这就是经典的 **check-then-act 竞态条件**。

**解决方案——数据库条件更新**：

```java
// ✅ 正确写法 —— 用 UPDATE 的原子性做抢占
public void onMessage(AiTaskMessage message) {
    int affectedRows = aiTaskRepository.claimTask(message.getTaskId(), workerId);
    //              ↑ 对应 SQL:
    //              UPDATE ai_task SET status = 'RUNNING', worker_id = ...
    //              WHERE task_id = ? AND status IN ('PENDING', 'RETRYING')
    //              AND (next_retry_at IS NULL OR next_retry_at <= NOW())

    if (affectedRows == 0) {
        return;  // 被别人抢了，直接返回 ACK
    }
    executeTask(message.getTaskId());  // 只有抢到的 Worker 执行
}
```

数据库的 UPDATE 语句本身是**原子的**。两个 Worker 同时发 UPDATE：
- 一个返回 affectedRows = 1（抢到了）
- 另一个返回 affectedRows = 0（WHERE 条件不满足了）

不需要分布式锁，不需要 `synchronized`，只靠一条 SQL 就实现了多 Worker 下的互斥。

❓ **面试追问："如果 Worker 抢到任务后，代码执行到一半宕机了怎么办？"**

> 这就是**心跳 + 补偿**要解决的问题（见 4.3）。任务状态已经是 RUNNING 了，新消息来也不会被重新抢占（WHERE status IN ('PENDING','RETRYING') 不再满足），但 Worker 已经死了。所以需要补偿 Job 去扫描"RUNNING 但心跳超时"的任务。

### 4.3 心跳 + 补偿 —— 抢救"卡住"的任务

**场景**：Worker 成功抢到了任务，状态变成了 RUNNING。但在执行 `generateStream()` 的过程中，Worker 进程挂了。

**问题**：数据库里的 `status = 'RUNNING'`，但没有人真正在执行它。新到达的消息也绕过了它（因为 "RUNNING" 不在 `WHERE status IN ('PENDING','RETRYING')` 里）。这条任务就**永远卡住了**。

📦 **比喻**：骑手取了餐，状态变成"配送中"。然后骑手手机没电了。订单永远"配送中"，餐厅以为有人在送，顾客以为快到了。需要一个系统去发现"这个骑手已经 30 分钟没更新位置了，重新派单"。

**解决方案——两层机制**：

**第一层：心跳（Heartbeat）**

Worker 在执行过程中定期更新 `heartbeat_at` 字段：

```java
// executeTask 中，每生成一段内容后更新心跳（节流处理，不是每个 token 都写库）
aiTaskRepository.touchHeartbeat(taskId);
// → UPDATE ai_task SET heartbeat_at = NOW() WHERE task_id = ?
```

就像骑手每 2 分钟上报一次 GPS 位置。

**第二层：补偿任务（Recovery Job）**

一个后台定时任务，扫描"该有心跳但没有心跳"的任务：

```java
@Scheduled(fixedDelay = 30_000)  // 每 30 秒检查一次
public void recoverStaleTasks() {
    // 查出心跳超过 5 分钟没更新的 RUNNING 任务
    List<AiTask> staleTasks = aiTaskRepository.findStaleRunning(
        timeoutMinutes = 5
    );
    for (AiTask task : staleTasks) {
        // 事务内: 标记 RETRYING + 创建新的 Outbox 事件
        aiTaskRepository.markRetrying(task.getTaskId());
        outboxRepository.save(OutboxEvent.newRetryEvent(task.getTaskId()));
    }
}
```

补偿被触发后，任务从 RUNNING 回到 RETRYING 状态，并且产生一个新的 Outbox 事件。这个事件最终会被发到 RocketMQ，被某个 Worker 重新抢占。

```
正常流程: PENDING → RUNNING → SUCCESS/FAILED
异常恢复: RUNNING → (心跳超时) → RETRYING → RUNNING → SUCCESS/FAILED
                                          ↑ 补偿 Job 创建新 Outbox 事件
```

❓ **面试追问："如果 Worker 其实没挂，只是心跳更新慢了，补偿 Job 把它标记成 RETRYING 了会发生什么？"**

> 这是一个经典竞态。缓解措施：心跳超时阈值设得足够大（比如 5 分钟），心跳更新频率足够高（比如 15 秒一次）。极端情况下如果 Worker 仍在执行且调了 `markSuccess()`，而任务状态已被改为 RETRYING，`markSuccess()` 需要做状态检查——只允许 RUNNING → SUCCESS 的转换。

### 4.4 异常分类 + 有限重试 —— 什么时候该重试，什么时候该放弃

**场景**：模型调用失败有很多原因——网络抖了一下、今天的 API 额度用完了、Prompt 太长被拒绝、JSON 解析出错……

**问题**：如果所有失败都重试，可能永远失败（比如 Prompt 格式错误），白白消耗重试配额和模型额度。如果所有失败都不重试，临时的网络超时也会导致任务失败。

📦 **比喻**：你打电话给朋友。占线（临时故障）→ 等 5 分钟再打。空号（永久故障）→ 别打了。

**解决方案——三类异常：**

```java
public void executeTask(Long taskId) {
    try {
        // ... Agent 编排 ...
        aiTaskRepository.markSuccess(taskId, finalContent);
    } catch (RetryableAgentException e) {
        // ① 可重试异常: 网络超时、API 限流(429)、临时 5xx
        aiTaskRepository.markRetrying(taskId, e.getMessage());
        throw e;  // ← 必须重新抛出! RocketMQ 才会触发重试
    } catch (Exception e) {
        // ② 不可重试异常: 参数错误、权限不足、JSON 解析失败
        aiTaskRepository.markFailed(taskId, e.getMessage());
        // 不抛出 → RocketMQ 认为消费成功 → 不再重试
    }
}
```

| 分类 | 例子 | 行为 |
|------|------|------|
| 可重试 | 连接超时、API 429、临时 5xx | 标记 RETRYING，重新投递（最多 2~3 次） |
| 不可重试 | 参数非法、任务不存在、JSON 解析异常 | 标记 FAILED，停止重试 |
| 超过重试上限 | 重试 3 次仍失败 | 进入 DLQ（死信队列），人工介入 |

❓ **面试追问："为什么 AI 任务不能无限重试？"**

> 每次重试都是一次模型 API 调用，消耗 token 额度和费用。而且 AI 输出有随机性，同样的 Prompt 再次执行可能生成不同内容（不做重复调用的去重保证）。所以只对明确的临时故障做有限重试。

### 4.5 DLQ（死信队列） —— 重试到最后一站

**场景**：一条消息重试了 3 次还是失败。RocketMQ 不会无限重试，会把这条消息扔进一个特殊的 Topic。

**什么是 DLQ**：当一条消息的消费重试次数达到上限（配置的 `max-reconsume-times`），RocketMQ 自动将它移到一个专门的 Topic（默认叫 `%DLQ%ai-writing-worker-group`）。这个 Topic 里的消息不会被自动消费——它们等着人工处理。

📦 **比喻**：快递三次派送都失败（地址不对、电话不通），包裹进入"疑难件"区域，需要客服人工打电话确认。

在本项目中，DLQ 的价值是：不会有任务"静默消失"。所有反复失败的消息都集中在一个地方，可以写一个后台页面或者监控告警来查看和手动重试。

---

## 五、SSE 改造——把"展示通道"和"执行开关"拆开

### 5.1 改造前后的本质区别

**改造前**：SSE 连接 = 任务启动的"开关"。前端连上才执行，断开就不执行了。

**改造后**：SSE 连接 = 纯"展示通道"。任务由 MQ Worker 独立执行，SSE 只负责把已经生成的内容推给浏览器。

```
改造前: Controller.stream()
  → 创建 emitter
  → runAsync(() → generateStream())     ← 在这里启动任务!
  → 边生成边推

改造后: Controller.stream()
  → 校验 taskId 归属
  → 查 task 当前状态
  → PENDING/RETRYING → 返回 409, 让前端轮询
  → RUNNING → 订阅事件, 补读最后一个 eventId 之后的事件
  → SUCCESS/FAILED → 返回终态, 让前端读最终结果
  → 注意: 这里再也看不到 runAsync 和 generateStream 了!
```

### 5.2 为什么 RUNNING 才建 SSE，其他状态轮询

想象一个场景：100 个用户同时提交了 AI 写作任务，但你的 Worker 同时只能执行 10 个。

**如果用老方案**：100 个用户都打开 SSE 连接等着 → 100 个长连接，其中 90 个只是干等 → 大部分连接浪费了服务器的 socket、文件描述符和内存。

**新方案**：90 个 PENDING 任务的用户用轮询（每 2~10 秒发一次 HTTP 请求查状态），只有 10 个 RUNNING 任务的用户才建立 SSE。

```
100 个排队任务:
  改造前 → 100 条 SSE 连接 (90 条干等)
  改造后 → 10 条 SSE 连接 + 90 个轮询请求 (每个请求瞬间结束)
```

📦 **比喻**：银行办业务。改造前 = 所有人都在柜台前排队站着（占着窗口资源但大部分时候在等）。改造后 = 拿号等着（轮询），叫到号的人才去柜台（建 SSE），办完立刻离开。

### 5.3 事件分发的双通道设计

这是本方案最容易困惑但也最巧妙的地方。我们用两个 Redis 机制，各管各的事：

```
Worker 执行 generateStream()
  │
  ├─→ Redis Stream: XADD ai:task:stream:{taskId}  {phase, type, content}
  │       作用: 持久保存事件 (保留 24 小时), 用于断线补读
  │
  └─→ Redis Pub/Sub: PUBLISH task-event-live  {taskId, event}
          作用: 即时通知 SSE Gateway "有新事件"
```

**为什么要两个？一个不行吗？**

| 只用 Stream | 只用 Pub/Sub |
|---|---|
| 每条 SSE 需要开一个 XREAD BLOCK 阻塞读 → 1000 条 SSE = 1000 个阻塞线程 | 断线即丢事件, 无法补读 |
| Stream 是持久化的，补读没问题 | Pub/Sub 是纯在线广播，不存数据 |

所以结论是两者配合：
- **Stream** 是"回放日志"——断线重连后说"我从第 52 条事件之后断了，把 53 到现在的给我"
- **Pub/Sub** 是"在线通知"——有事件立刻推，不需要每条 SSE 去轮询 Stream

📦 **比喻**：
- Stream = 监控录像。你离开期间发生的事，回放可以补看。
- Pub/Sub = 实时监控画面。你盯着看的时候，画面实时更新。
- 你不能用 Pub/Sub 补看录像（没存），也不能给每台显示器配一个录像回放员（太费资源）。

### 5.4 断线续读是怎么实现的

```
浏览器连上 SSE, 服务器推了 50 条 token 事件
  ↓ 网络断了 3 秒
  
浏览器自动重连:
  GET /api/v1/ai-writing/task/stream?taskId=xxx&lastEventId=1720000000049-0
                                                      ↑ 最后收到的事件 ID

服务端:
  1. 查 task 状态 → RUNNING, 继续
  2. XREAD Redis Stream: ai:task:stream:{taskId}
     FROM ID > 1720000000049-0
  3. 把 Stream 里 50 到现在的所有事件先推给浏览器
  4. 然后继续订阅 Pub/Sub 实时事件
```

这里 `lastEventId` 就是 Redis Stream 的 Record ID（比如 `1720000000049-0`），XREAD 可以从指定位置开始读。

**注意**：原生浏览器 `EventSource` API 在断线自动重连时会自动带 `Last-Event-ID` 头，但首次手动连接时不支持自定义 Header。所以第一版我们用 URL 查询参数 `?lastEventId=xxx` 来传。

---

## 六、完整链路演练——从提交到完成的每一步

> 这一节让你看到在每个节点"数据长什么样"。建议对照 mq-final-plan.md 的状态机读取。

### 时刻 0：用户提交任务

```
POST /api/v1/ai-writing/task/submit
  draftId=42, taskType=GENERATE_BODY, enableIllustration=true

→ AiWritingService.submitTask()  [@Transactional]
    ai_task:   id=10001, status=3(PENDING), prompt_payload="你是一个高级..."
    outbox_event: event_id=1, event_type=AI_WRITING_TASK_CREATED,
                  aggregate_id=10001, status='NEW'

返回: { taskId: 10001, status: 3, statusDesc: "待处理" }

前端: 开始轮询 GET /task/10001, 每 2 秒一次
      显示 "任务排队中..."
```

### 时刻 1：Outbox Publisher 投递 (~1秒后)

```
OutboxPublisher (每1秒扫描一次)
  → SELECT * FROM outbox_event WHERE status='NEW' LIMIT 100
  → 找到 event_id=1
  → rocketMQTemplate.syncSend("ai-writing-task", {taskId: 10001, eventId: 1})
  → RocketMQ Broker 返回 SEND_OK
  → UPDATE outbox_event SET status='PUBLISHED', published_at=NOW() WHERE event_id=1
```

### 时刻 2：Worker 抢占并启动

```
AiTaskConsumer (RocketMQ 推送消息)
  → onMessage({taskId: 10001})

  → claimTask(10001, workerId="worker-abc-123"):
     UPDATE ai_task SET status=0(RUNNING), started_at=NOW(), heartbeat_at=NOW(),
            worker_id='worker-abc-123'
     WHERE id=10001 AND status IN (3, 4)  -- PENDING(3) or RETRYING(4)
     → affectedRows = 1 ✅ 抢到了!

  → executeTask(10001)
```

### 时刻 3：生成过程中

```
executeTask(10001):
  → 从 DB 读取 task, 获取 promptPayload
  → 创建 Agent 会话, 调用 handleMessageStream().blockingForEach()
  
  每收到一个 token:
    → 写 Redis Stream: XADD ai:task:stream:10001 * phase generating type token content "..."
    → 发 Redis Pub/Sub: PUBLISH task-event-live {taskId:10001, ...}
    → 每 15 秒: UPDATE ai_task SET heartbeat_at=NOW() WHERE id=10001
```

### 时刻 4：前端发现 RUNNING，建立 SSE

```
前端轮询 GET /task/10001
  → 返回 { status: 0(RUNNING), statusDesc: "生成中", ... }

前端调用: connectTaskStream(10001)

GET /api/v1/ai-writing/task/stream?taskId=10001

Controller:
  → 查 task → status=RUNNING ✅
  → 订阅 Redis Pub/Sub: task-event-live
  → 进入 SSE 循环: 收到 Pub/Sub 消息 → 匹配 taskId → 推给 emitter

浏览器实时收到:
  event: status, data: {"phase":"analyzing","content":"正在分析草稿上下文..."}
  event: token,  data: {"phase":"generating","content":"消息队列是..."}
  event: token,  data: {"phase":"generating","content":"一种在分布式系统中..."}
  ...
```

### 时刻 5：生成完成

```
executeTask() 最后:
  → markSuccess(10001, formattedContent):
     UPDATE ai_task SET status=1(SUCCESS), response_content=..., update_time=NOW()
     WHERE id=10001

  → Redis Stream: XADD ai:task:stream:10001 * type done content ""
  → Redis Pub/Sub: PUBLISH task-event-live {taskId:10001, type:done}

SSE Controller 收到 done:
  → emitter.send("event: done\ndata: ...\n\n")
  → emitter.complete()  ← 关闭 SSE 连接

浏览器:
  → 收到 done 事件
  → EventSource 触发关闭
  → 从 responseContent 渲染最终文章 (或再次 GET /task/10001 获取)
```

### 时刻 6（异常分支）：Worker 执行到一半挂了

```
Worker-A 已经抢到 10001, status=RUNNING, heartbeat_at=13:00:00
  → 执行到一半 → JVM OOM → 进程退出

13:05:00  Recovery Job 扫描:
  → SELECT * FROM ai_task
     WHERE status=0(RUNNING)
       AND heartbeat_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)
  → 发现 taskId=10001, heartbeat_at=13:00:00, 已超时 5 分钟

  @Transactional:
    UPDATE ai_task SET status=4(RETRYING), next_retry_at=NOW()
    INSERT outbox_event (event_type=AI_WRITING_TASK_CREATED, status='NEW')

  → Outbox Publisher 扫描到新事件 → 发到 RocketMQ
  → Worker-B 消费消息 → claimTask(10001)
     → WHERE status IN (3,4) ← RETRYING(4) 符合条件
     → affectedRows = 1 ✅
  → 重新执行
```

---

## 七、几种替代方案的对比如下

> 这一节让你理解"为什么是这套方案"——每种被放弃的方案都有它的道理，只是不适合本项目的场景。

### 方案 A：保持现状（ForkJoinPool + SSE）

**优点**：代码简单，你现在的代码就在跑。

**缺点**：第一、二节列出的 5 个问题一个都解决不了。这不是"技术选型"，而是"欠技术债"。

**用不用**：不采用。

---

### 方案 B：专用线程池 + SSE（不加 MQ）

```java
// ThreadPoolConfig 加一个 Bean
@Bean("aiWritingExecutor")
public ThreadPoolExecutor aiWritingExecutor() { ... }

// Controller 里指定线程池
CompletableFuture.runAsync(() -> { ... }, aiWritingExecutor);
```

**优点**：
- 改动极小（加个 Bean，改一行 Controller）
- 解决了 ForkJoinPool 被占满的问题
- 可以限制 AI 任务并发数

**缺点**：
- 队列在内存里 → 进程重启丢任务
- 任务还是靠前端连 SSE 才启动 → 离线不执行
- 不能独立扩容 Worker
- 没有持久化的重试和 DLQ

**用不用**：保留作应用内辅助线程池（比如 Outbox Publisher 和 Recovery Job），但不承担 Agent 主任务调度。

---

### 方案 C：MQ + 纯轮询（不要 SSE）

```
MQ 调度任务 → Worker 执行 → 结果存 MySQL → 前端轮询查结果
```

**优点**：
- 架构最简单，没有 SSE 长连接管理的烦恼
- 后端实现最省事

**缺点**：
- 用户看不到实时 token 生成过程——这是 AI 写作的核心体验
- 一个 3 分钟的任务如果每 2 秒轮询一次 = 90 次 HTTP 请求，而且每次只能看到完整结果
- 润色、摘要等短任务还行，续写长文完全不行

**用不用**：不采用。SSE 流式体验是本产品的核心差异化体验。

---

### 方案 D：直接 `save + syncSend`（不用 Outbox）

```java
aiTaskRepository.save(task);                  // ① 保存
rocketMQTemplate.syncSend(topic, message);    // ② 发消息
```

**优点**：看起来简单直白。

**缺点**：①② 不在同一事务，存在经典的"双写不一致"窗口：
- ① 成功 ② 失败 → 任务永久 PENDING
- ② 成功 ① 回滚 → Worker 查不到任务

**用不用**：不采用。Transactional Outbox 是本方案可靠性的基石。

---

### 方案 E：RocketMQ 事务消息

RocketMQ 原生支持事务消息（半消息 + 本地事务检查 + 回查），理论上可以替代 Outbox。

**优点**：不需要额外的 `outbox_event` 表和 Publisher。

**缺点**：
- 事务回查机制的排障和运维复杂度高
- Outbox 的优势在于**可审计**——`outbox_event` 表本身就是消息投递的审计日志
- 新手友好度差

**用不用**：可作为后续替换方案。本项目优先使用 Outbox（可审计、可排查、可重放）。

---

## 八、限流、并发、MQ —— 三个不同层次的"门"

很多初学者会把限流、并发控制和 MQ 搞混。它们是三个独立的"门"，管不同的事：

```
用户请求 → [门1: 速率限制] → [门2: 任务入队] → [门3: 消费并发] → 执行
           Redis 令牌桶      RocketMQ 队列     Consumer 线程数
           每人每分钟 N 次    无限制积压         同时执行 M 个
```

| 机制 | 管什么 | 如果没它会怎样 |
|------|--------|---------------|
| **速率限制** (Redis RRateLimiter) | 单用户提交频率 | 一个用户狂点把系统打爆 |
| **RocketMQ 队列** | 超出即时处理能力的任务暂存 | 系统忙时直接拒绝/丢弃请求 |
| **Consumer 并发** (线程数) | 同时调用模型的并发数 | 模型 API 被超出额度的并发打爆 |

**当前的速率限制**（每人每分钟 5 次，`RateLimitService.java`）只解决了单用户滥用。MQ 上线后要注意：**100 个不同用户同时提交**可以绕过速率限制，因为它们不在同一令牌桶里。这 100 个任务会进入 RocketMQ 排队，由 Consumer 线程数决定并发——这才是第二道防线。

所以：**速率限制保护系统不被单用户打爆，Consumer 并发保护模型 API 不被总并发打爆。**

---

## 九、帮你理解面试中怎么说

> 这一节不是"背答案"，而是帮你把这些概念串成自己的理解。面试官追问时，能从"你项目里的真实问题"出发回答。

### 9.1 叙述逻辑

推荐的叙述顺序——从**你代码的具体问题**出发，而不是从"MQ 很好"出发：

1. **先说现状**：我现有的代码里，AI 写作的执行入口是 SSE Controller 里的 `CompletableFuture.runAsync()`，跑在公共 ForkJoinPool。任务状态存在 MySQL。
2. **再说痛点**：这导致几个问题——用户关浏览器任务不执行；进程崩溃正在跑的任务永远卡住；Web 和 Worker 绑死不能分开扩容。
3. **然后说方案**：所以我把"接收任务"和"执行任务"拆开，中间接入 RocketMQ。提交时事务保存任务 + Outbox 事件，后台 Publisher 投递；Consumer 用条件 UPDATE 原子抢占防止重复执行；心跳 + 补偿让超时任务复活；可重试和不可重试异常分开处理。
4. **最后说体验**：流式体验通过 Redis Stream + Pub/Sub 保留：Stream 存短期事件用于断线续读，Pub/Sub 做实时推送。SSE 只在任务 RUNNING 后建立，PENDING 状态用前端退避轮询，长连接数从"排队数"收敛到"执行数"。

### 9.2 高频追问及回答思路

**Q：数据库里已经有 ai_task 表了，为什么还需要 MQ？**

> ai_task 记录的是"任务是什么，最终结果是什么"——它是状态存储。MQ 解决的是"谁来执行、什么时候执行、执行失败了怎么办"——它是调度问题。就像你手机里有所有联系人的电话（数据库），但打电话这个动作需要一个拨号器（MQ）。

**Q：为什么不用 `@Async` 注解？**

> `@Async` 适合"发个通知、记录个日志"这种几十毫秒的轻量异步任务。AI 写作是分钟级的模型调用，需要持久排队（重启不丢）、重试、DLQ、独立扩容，这些都是线程池做不了的。

**Q：Consumer 执行一半宕机，会不会重复生成？**

> 两层面：① 条件 UPDATE 防止两个不同 Consumer 同时执行同一个 taskId（数据库原子操作保证）；② 但模型请求发出去后 Worker 崩溃这个临界窗口——模型供应商已经收到并开始生成了——确实无法用应用层代码回滚。所以加入心跳 + 补偿 + 有限重试，并建议在模型 API 层用 taskId 作为幂等键（如果供应商支持）。

**Q：为什么 Redis Stream 而不是 Pub/Sub 单独用？**

> Pub/Sub 是纯在线广播，订阅者不在线就收不到。Stream 是持久化日志，支持按 ID 回放。所以 Pub/Sub 做实时推送，Stream 做断线补读。两个各管各的，不是二选一。

**Q：积压了怎么办？能直接加 Consumer 线程吗？**

> 先看瓶颈在哪：模型 API 还有 QPS 额度吗？Worker CPU/内存够吗？如果模型调用本身就是瓶颈，加线程只会让 API 限流更频繁。正确的做法是先看配额，有余量才扩 Consumer，没余量就限流入口或给用户显示排队状态。

### 9.3 简历怎么写

不要写：
> 使用 RocketMQ 实现异步削峰

这样写：
> 将 AI 写作从 SSE 触发的本机异步执行改造为 RocketMQ 后台任务：通过 Transactional Outbox 保证任务创建与消息投递的最终一致，数据库条件更新实现 Consumer 原子抢占，结合心跳补偿、有限重试、DLQ 与 Redis Stream/SSE 续读，实现长任务的可靠排队、故障恢复和流式输出解耦。

关键区别：**前者只说了"我用了 RocketMQ"，后者说清了"我为什么用、解决了什么真实问题、如何闭环"。**

---

## 十、核心思想

整个方案可以归结为一句话：

> 把"接收任务"和"执行任务"拆开，中间用 RocketMQ 作为持久化的待办队列；用 Outbox 保证"命令一定送达"；用条件 UPDATE 保证"命令只执行一次"；用 Redis Stream + Pub/Sub 保证"执行过程用户能看到、断线后能补看"。

这六个"保证"构成了方案的全部：
1. **Outbox** → 命令一定送达 MQ（不丢消息）
2. **条件 UPDATE** → 同一个任务只被一个 Worker 执行（不重复跑）
3. **心跳 + 补偿** → Worker 崩溃的任务能被重新调度（不卡住）
4. **异常分类 + 有限重试 + DLQ** → 临时故障重试、永久故障停止、超限人工介入（不白烧钱）
5. **Redis Stream** → 断线后的过程事件可以补读（不漏 token）
6. **Redis Pub/Sub** → 在线用户实时收到事件，无需轮询（不浪费连接）
