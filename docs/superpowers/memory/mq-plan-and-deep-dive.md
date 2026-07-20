# RocketMQ 异步削峰：实现方案 + 面试深度解析

---

## 一、当前痛点

当前 AI 写作任务的执行链路是"同步＋异步混搭"：

```
POST /ai-writing/task/submit
  → submitTask()：创建 task（PENDING）、落库、返回 taskId（同步）
  → 前端拿 taskId 后再调 GET /ai-writing/task/stream
  → stream(): CompletableFuture.runAsync() 用 ForkJoinPool 线程调用 generateStream()
  → SSE 连接挂住 30-180s，边生成边推送
```

**问题在哪？**

1. ForkJoinPool 是全局共享的，默认 coreSize 为 CPU 核数 - 1。当前项目 `ThreadPoolConfig` 里给 `memoryExecutor` 单独配了线程池，但 `CompletableFuture.runAsync()` 没指定线程池时走的就是 ForkJoinPool.commonPool()。一旦并发高（比如 10 个人同时点"生成"），ForkJoinPool 线程全被 SSE 长连接占满，后续请求直接排队等。
2. SSE 连接本身有超时限制（当前设了 10 分钟），复杂任务如果超时，连接断开后前端不知道任务最终是成功还是失败。
3. 任务状态对前端不可见——用户只能盯着一个转圈，体验差。

---

## 二、方案设计

### 2.1 核心思路

把"任务执行"从 SSE 同步链路中拆出来，变成异步队列消费：

```
                   原有方案                               改造后方案
          ┌───────────────────────┐          ┌──────────────────────────┐
用户提交 → submitTask() 创建记录     用户提交 → submitTask() 创建记录
          │                        │          │                         │
          │  返回 taskId            │          │  发 MQ 消息               │
          │                        │          │  返回 taskId（毫秒级）     │
          ▼                        │          ▼                         │
  前端 SSE 连接 → ForkJoinPool      │    RocketMQ → Consumer 异步消费
  挂住 30-180s → 推送结果           │          │                         │
          │                        │          │  generateStream() 执行
          │  任务结束                │          │  更新 DB（SUCCESS/FAILED）
          │                        │          ▼                         │
                                前端轮询 GET /task/{taskId} 查状态
                                或 SSE 订阅任务进度事件
```

**核心变化**：SSE 不再挂住等 Agent 生成完。SSE 只用来"订阅"任务状态变化——生成完成时 Consumer 往 SSE 连接推一条完成通知就断开。

### 2.2 消息模型

两个 Topic：

| Topic | 场景 | 消息体 |
|-------|------|--------|
| `ai-writing-task` | AI 写作任务异步化 | `{taskId, userId, draftId, taskType, promptPayload, enableIllustration}` |
| `article-event` | 发布后异步统计 | `{type: "published", articleId}` |

### 2.3 改动文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `pom.xml` | 修改 | 新增 `rocketmq-spring-boot-starter:2.3.0` |
| `application-dev.yml` | 修改 | RocketMQ namesrv 地址 + producer/consumer 配置 |
| `docker-compose-environment.yml` | 修改 | 新增 RocketMQ NameServer + Broker 容器 |
| `infrastructure/mq/config/RocketMQConfig.java` | **新建** | Producer/Consumer Bean 配置 |
| `infrastructure/mq/producer/AiTaskMessageProducer.java` | **新建** | 发送 AI 任务消息 |
| `infrastructure/mq/producer/ArticleEventProducer.java` | **新建** | 发送文章事件消息 |
| `infrastructure/mq/consumer/AiTaskConsumer.java` | **新建** | 消费 AI 任务，调用 generateStream() |
| `infrastructure/mq/consumer/ArticleEventConsumer.java` | **新建** | 消费文章事件，异步更新元数据 |
| `AiWritingController.java` | 修改 | 新增 `GET /task/{taskId}/status` 轮询接口 |
| `AiWritingService.java` | 修改 | `submitTask()` 改为发 MQ；新增 `executeTask()` 供 Consumer 调用 |
| `PublishDomainService.java` | 修改 | `publish()` 末尾发 MQ 异步事件 |

### 2.4 关键代码示意

**Producer——发送可靠投递**：

```java
// 同步发送 + 重试
rocketMQTemplate.syncSend("ai-writing-task", message, 
    new SendCallback() { ... }, 3000 /* timeout */);
```

`syncSend` 会等 Broker 返回 ACK 才返回，如果超时或失败抛异常，生产者侧可重试。这是保证"消息一定到了 Broker"的关键。

**Consumer——幂等消费**：

```java
@RocketMQMessageListener(topic = "ai-writing-task", consumerGroup = "ai-task-group")
public class AiTaskConsumer implements RocketMQListener<MessageExt> {
    public void onMessage(MessageExt msg) {
        AiTaskMessage taskMsg = JSON.parseObject(msg.getBody(), AiTaskMessage.class);
        AiTaskEntity task = aiTaskRepository.queryById(taskMsg.getTaskId());
        // 幂等判断：状态已经不是 PENDING 就跳过
        if (task.getStatus() != AiTaskStatusVO.PENDING) {
            return;  // 已处理过
        }
        // 执行生成
        aiWritingService.executeTask(task);
    }
}
```

消费失败处理：抛异常 → RocketMQ 自动 `RECONSUME_LATER`（默认重试 16 次），超过重试次数进死信队列 `%DLQ%ai-task-group`，后续人工处理。

**前端轮询接口**：

```java
// 新增 GET /ai-writing/task/{taskId}/status
// 返回 {status, progress, errorMsg}
// 前端每 2s 轮询一次，SUCCESS 时拿 responseContent 渲染
```

### 2.5 Docker Compose 新增服务

```yaml
rocketmq-namesrv:
  image: apache/rocketmq:5.3.0
  container_name: rmq-namesrv
  ports:
    - "9876:9876"
  command: sh mqnamesrv

rocketmq-broker:
  image: apache/rocketmq:5.3.0
  container_name: rmq-broker
  ports:
    - "10911:10911"
  environment:
    NAMESRV_ADDR: rocketmq-namesrv:9876
  command: sh mqbroker -n rocketmq-namesrv:9876 -c /home/rocketmq/conf/broker.conf
  depends_on:
    - rocketmq-namesrv
```

**自动创建 Topic**：在 `broker.conf` 中配置 `autoCreateTopicEnable=true`，或在 `RocketMQConfig` 中通过 `@PostConstruct` 初始化时用 admin API 创建 Topic。

---

## 三、面试深度解析

### 3.1 聊到这里，面试官在想什么？

当你在简历上写"MQ 异步削峰"时，面试官关心的不是"你用了 RocketMQ"（调用一个 jar 包谁都会），而是：

1. **你有没有想过为什么需要？**——不能说"别人用我也用"，要能从自己的业务场景出发解释痛点
2. **你怎么保证消息不丢？**——Producer、Broker、Consumer 三条路上各有什么手段
3. **你怎么保证不重复消费？**——幂等方案的选择和 trade-off
4. **消息积压了怎么办？**——是扩容 Consumer 还是有其他手段
5. **和令牌桶限流是什么关系？**——两者的定位、解决的问题、协作方式

### 3.2 从"痛点驱动"出发讲故事

面试的标准节奏是"问题→方案→实现→踩坑→收获"。你讲 MQ 这一块时，用这个套路：

> 我之前写了一个 AI 写作系统，用户提交任务后，Agent 需要 30 秒到 3 分钟来生成文章。目前的做法是——用户提交后，前端连上 SSE，后端用 `CompletableFuture.runAsync()` 扔到 ForkJoinPool 里去跑，SSE 连接就一直挂着等结果。
>
> 后来我想，这个架构有两个问题。第一，ForkJoinPool 是全局共享的，线程数等于 CPU 核数减一。万一同时有十几个人点"生成"，线程就打满了，其他用 ForkJoinPool 的地方也会被影响。第二，SSE 连接长时间挂着本身就是一种资源浪费。
>
> 所以我引入了 RocketMQ。用户提交后毫秒级返回一个 taskId，任务进 MQ 排队，由 Consumer 慢慢消费。前端呢，用一个简单的轮询接口——每两秒查一下 `/task/{taskId}/status`，看到 `SUCCESS` 就拿结果渲染。

### 3.3 消息可靠性：三段式保障

面试官问"你怎么保证消息不丢"，标准答案要分三段讲：

| 阶段 | 丢消息场景 | 保障手段 |
|------|-----------|---------|
| **Producer → Broker** | 网络抖动、Broker 宕机 | `syncSend` 同步发送等 ACK，失败重试 3 次 |
| **Broker 存储** | Broker 宕机前消息还在内存 | 异步刷盘改成同步刷盘（性能换可靠性），或主从同步复制 |
| **Consumer → 业务** | 消费到一半挂了，消息没 ACK | 业务处理完再返回 `CONSUME_SUCCESS`；抛异常走 `RECONSUME_LATER` |

**校招生加分表述**：

> 发送我用的是 syncSend，同步等 Broker 确认才返回，不是发完就不管了。消费端我做的幂等——消费前先查 DB 里这个任务的状态，如果不是 PENDING 就直接跳过，说明之前已经处理过了。这样即使 MQ 重发了同一条消息也不会重复执行。

### 3.4 幂等消费的方案选择

简历上写"幂等消费"很常见，面试官会顺着问"你怎么做的"。常见的三种方案：

| 方案 | 怎么做 | 优点 | 缺点 |
|------|-------|------|------|
| **状态机（本项目）** | 消费前查 task.status，PENDING 才处理 | 简单，和业务字段复用 | 依赖 DB 查询 |
| 唯一键 | 消息里带 bizId，Consumer 插入去重表 | 不依赖业务状态 | 多一次 DB 写入 |
| Redis 去重 | 消费前 `SETNX msgId`，成功才处理 | 快 | 数据可能丢（Redis 不是持久存储） |

**本项目用的是状态机方案**。因为 `AiTaskEntity` 本身就有 PENDING→RUNNING→SUCCESS/FAILED 的状态流转，天然支持幂等判断，不需要额外引入去重表。

**面试时这样说**：

> 我的任务是 PENDING→RUNNING→SUCCESS/FAILED 四个状态的流转。Consumer 拿到消息后先查这个任务的状态，如果不是 PENDING 就直接 return，说明这消息之前已经消费过了。这个方案的优点是——状态自己就是幂等键，不需要额外建去重表。

### 3.5 消费失败与死信队列

RocketMQ 的默认重试：`RECONSUME_LATER` → 延迟重试（10s/30s/1m/2m...）→ 最多 16 次 → 进死信队列 `%DLQ%{consumerGroup}`。

**面试时可以说**：

> 消费失败的重试不是我手写的，RocketMQ 自带——抛异常返回 `RECONSUME_LATER`，它就会延迟重试。默认重试 16 次，超过后自动进死信队列。死信队列里的消息我不会自动重试，而是打日志告警、人工排查——因为能重试 16 次都失败，大概率不是瞬时故障（比如网络抖动），而是代码 bug 或者上游服务问题。

---

## 四、令牌桶限流 vs 消息队列削峰：它们根本不是一回事

这是面试官很喜欢问的对比题。表面上两者都在"控制流量"，但解决的问题完全不同。

### 4.1 一句话区分

| | 令牌桶限流 | 消息队列削峰 |
|---|-----------|------------|
| 干什么 | **拒绝请求**（"你别来了"） | **排队请求**（"你先等着"） |
| 保护谁 | 保护上游 API（DeepSeek/硅基流动）配额不被超限调用 | 保护自身系统资源（线程池、SSE 连接）不被瞬时流量打满 |
| 用户感知 | 超限 → 直接报错"请求过于频繁" | 提交成功 → 毫秒级返回 taskId → 排队等结果 |
| 处理方式 | 超过阈值直接拒绝，不排队 | 先进先出排队，异步消费 |
| 失败模式 | 主动丢弃（fail-fast） | 延迟处理（delay + retry） |

### 4.2 形象类比

- **令牌桶 = 景区限流**：每天只卖 5000 张票，卖完就关门。你在门口被拦住，告诉你"明天再来"。
- **消息队列 = 银行取号**：你进了门，取了号，坐在椅子上等着叫号。你知道你的事儿一定会被办，只是不知道具体几点轮到你。

### 4.3 本项目的具体差异

```
用户发起 AI 写作请求
        │
        ▼
  ┌─────────────┐
  │ 令牌桶限流    │  ← RRateLimiter: 每分钟 5 个令牌
  │ 超限直接拒绝   │     保护 DeepSeek API 不被调用太猛
  └──────┬──────┘
         │ 通过（拿到了令牌）
         ▼
  ┌─────────────┐
  │ RocketMQ    │  ← 消息队列：任务排队
  │ 削峰填谷     │     保护 ForkJoinPool 不被长任务占满
  └──────┬──────┘
         │
         ▼
  ┌─────────────┐
  │ Consumer    │  ← 逐个消费，调 generateStream()
  │ 异步执行     │
  └─────────────┘
```

**为什么两个都要，而不是只用一个？**

> 如果只有令牌桶：通过的请求仍然同步执行，并发高时线程池打满，后来的请求排队等 ForkJoinPool（不是等 MQ，是等线程），最终超时报错。
>
> 如果只有 MQ：没有令牌桶在门口拦着，恶意用户可以在 1 分钟内刷 500 个请求进 MQ 队列，DeepSeek API 配额瞬间耗尽，账号被封。消息队列帮你"排队"，但不管"来多少"。
>
> 令牌桶控"量"（flow rate），消息队列控"峰"（burst）。两者组合 = 两级流控。

### 4.4 面试话术

> 令牌桶和消息队列解决的是不同层面的问题。令牌桶是入口的"守门员"——每分钟只放 5 个请求过去，超了直接拒绝。消息队列是里面的"候诊室"——通过守门员的请求进来后不是立刻执行，而是先进队列排队，由 Consumer 逐个消费。令牌桶保护的是上游 API 的调用额度，消息队列保护的是我自身系统的线程资源。两个组合起来，就是一个简单的两级流控。

---

## 五、面试常见追问

### Q1: 消息积压了怎么办？

> 首先排查是不是 Consumer 处理太慢了——如果单条消息处理要 3 分钟，Consumer 线程数又不够，必然积压。方案是增加 Consumer 实例数（横向扩容），或者把 Consumer 的 `consumeThreadMax` 调大。如果瓶颈在 DeepSeek API 的并发限制，那就没办法——只能让消息慢慢排着，或者在前端给用户一个预估等待时间。

### Q2: RocketMQ 和 Kafka 为什么选 RocketMQ？

> 第一，RocketMQ 的事务消息原生支持，Kafka 要到新版本才有。第二，RocketMQ 自带延迟消息（18 个级别），Kafka 没有这个能力。第三，RocketMQ 是阿里开源的，中文文档和社区讨论多，个人项目踩坑成本低。第四，RocketMQ NameServer 比 Kafka 依赖 ZK 更轻量，Docker 部署更快。

### Q3: 顺序消息？你这个场景需要吗？

> 我这个场景不需要严格顺序——每个 AI 任务是独立的，谁先执行完谁先返回结果。但如果同一个 draftId 的多次生成需要串行（比如先生成大纲再生成正文），可以让 MessageQueueSelector 按 draftId hash 把消息路由到同一个队列，RocketMQ 队列内是 FIFO 的。

### Q4: MQ 的引入带来了哪些新问题？

> 第一是系统复杂度——多了一个中间件要维护、监控。第二是最终一致性——以前同步执行，用户直接知道结果，现在异步了，需要轮询或 SSE 订阅来感知状态变化。第三是消息丢失风险——虽然有 syncSend + 重试 + 死信队列的保障，但如果 Broker 本身宕机且没有主从复制，消息确实有可能丢。这个在个人项目中可以接受，生产环境需要 Broker 集群 + 同步双写来保证。

---

## 六、实施步骤

| 步骤 | 内容 | 预估 |
|------|------|:--:|
| 1 | pom.xml 加 `rocketmq-spring-boot-starter:2.3.0` | 2min |
| 2 | docker-compose 加 NameServer + Broker | 5min |
| 3 | `application-dev.yml` 加 rocketmq 配置 | 2min |
| 4 | `RocketMQConfig.java` 写 Producer/Consumer Bean | 10min |
| 5 | `AiTaskMessageProducer.java` + `AiTaskConsumer.java` | 20min |
| 6 | `AiWritingService.submitTask()` 改发 MQ；抽 `executeTask()` | 10min |
| 7 | `AiWritingController` 新增 `GET /task/{taskId}/status` | 5min |
| 8 | `ArticleEventProducer.java` + `ArticleEventConsumer.java` | 10min |
| 9 | `PublishDomainService.publish()` 末尾发 MQ | 2min |
| 10 | 联调、验证 | 15min |

---

## 七、和简历其他点的关系

简历上 5 个核心工作点不是孤立的，面试时要能串起来：

```
用户点"AI 续写"
  → [4. Redis] 令牌桶判断是否超限 → 超限直接拒绝
  → [4. Redis] RLock 分布式锁防重复提交
  → [5. RocketMQ] 发消息进队列，毫秒级返回 taskId
  → [5. RocketMQ] Consumer 拿到消息，调用 Agent 链
  → [1. Agent 编排] analyst→generator→reviewer 三阶段处理
  → [2. 流式消费] RxJava 分流推送 SSE
  → [3. Agent 记忆] 注入用户偏好到 prompt
  → [5. RocketMQ] 完成后更新 DB，前端轮询到 SUCCESS
  → [5. RocketMQ] 发布后 MQ 异步统计（浏览量/缓存清理）
```

面试时可以顺着这个链路讲一遍，展示你理解的是**一个系统如何协作**，而不是**几个孤立的组件**。
