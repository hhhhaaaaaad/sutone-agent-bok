# 项目最终完成方案与时间线（final_plan）— 2026-07-08

> 本文档是**可执行的施工总纲**，整合三份前置文档并落到具体阶段、产出物、时间线：
> - `project-expansion-plan-for-recruitment-2026-07-08.md`（面试价值视角，方向与话术）
> - `next-development-roadmap-2026-07-08.md`（产品视角，用户体系/社区/编辑体验）
> - `project-progress-analysis-2026-07-07.md`（进度基线）
>
> 与前三份的关系：前面回答「做什么、为什么」，**本文档回答「按什么顺序做、每步交付什么、什么时候做完」**。

---

## 0. 关键前提（2026-07-08，结合你最新 5 条反馈）

1. **用户体系**：你会后期扩展 → 本方案把它作为**阶段一的前置依赖**排进去（多人协同、限流、鉴权都依赖真实用户）。
2. **多人在线编辑**：新增需求 → 作为**阶段七**单列，给出技术选型与最小实现（见第 9 节）。
3. **RAG + draw.io 配图编进工作流**：你已认可 → 第 7、8 节给出「如何嵌入现有 ADK/Armory 体系」的具体接入方式。
4. **按扩展文档第 8 节顺序推进 + 云服务器部署**：本文档主体（第 3~11 节）就是这个顺序的展开 + 部署方案。
5. **你已有大厂后端实习 + 真实后端项目**：说明你后端基础扎实，**Agent 差异化与后端工程深度两条线都全力做透**——本项目要同时成为「AI Agent 硬核」和「Java 后端工程」的双料证据，两条线不互相让路。

> 一句话定位：**AI Agent 差异化 + Java 后端工程深度，双核并进，都做到能被追问 5 分钟不露怯。**

---

## 1. 施工总览（一张表看懂全程）

| 阶段 | 主题 | 核心产出 | 预估工期 | 依赖 | 优先级 |
|------|------|---------|---------|------|--------|
| 阶段〇 | 消费链路修复（author 感知） | 写作结果不再被污染 + 阶段可视化 | 2-3 天 | 无 | P0 最先做 |
| 阶段一 | 真实用户体系 + JWT/Security | 去掉 `DEFAULT_USER_ID`，真实登录鉴权 | 4-5 天 | 阶段〇 | P0 |
| 阶段二 | 多 Agent 写作流水线 + draw.io 配图嵌入 | 规划→写作→配图→质检可编排 | 5-7 天 | 阶段〇 | P0 |
| 阶段三 | RAG 检索增强 | 写作前检索历史文章/知识库 | 5-7 天 | 阶段一 | P0 |
| 阶段四 | Redis 三场景 + 索引优化 | 缓存/限流/分布式锁 + explain 优化 | 4-5 天 | 阶段一 | P0 |
| 阶段五 | 工具调用可视化 | 前端展示 Agent 每步调用过程 | 3-4 天 | 阶段二 | P1 演示 |
| 阶段六 | 可观测性（traceId）+ 压测 | 全链路追踪 + 一份 QPS 报告 | 4-5 天 | 阶段一 | P1 |
| 阶段七 | 多人在线协同编辑 | 多光标实时协作 | 7-10 天 | 阶段一 | P2 亮点 |
| 阶段八 | MQ 异步场景 | AI 任务异步化 / 发布后异步统计 | 3-4 天 | 阶段四 | P1 |
| 阶段九 | 云服务器部署 | 公网可访问的 demo + 域名 | 3-5 天 | 阶段一 | P0 贯穿 |

> P0 = 秋招必须有；P1 = 强烈建议；P2 = 加分亮点，时间够再做。

<!-- PLACEHOLDER_TIMELINE -->

---

## 2. 时间线（2026-07 中 → 2026-08 秋招开始）

> 假设你从现在（7 月中）起，日均可投入 3-4 小时，周末更多。以「秋招 8 月启动前拿出可演示、可讲深的项目」为目标倒排。总窗口约 6-7 周。

```text
第 1 周（7/中）  阶段〇 消费链路修复 + 阶段一启动（用户体系后端）
                 ├─ D1-2  修 generateStream 的 author 感知（P0，立即见效）
                 └─ D3-7  UserEntity/JWT/Security 最小闭环

第 2 周          阶段一收尾 + 阶段二启动
                 ├─ 去掉所有 DEFAULT_USER_ID，鉴权贯通
                 └─ 多 Agent 写作流水线：规划→写作→质检串起来

第 3 周          阶段二收尾（draw.io 配图子工作流嵌入）+ 阶段九首次部署
                 ├─ 配图 Agent 复用 sequential_draw_process
                 └─ 云服务器第一次把 demo 跑起来（越早越好，避免最后踩部署坑）

第 4 周          阶段三 RAG（本项目最大空白，重点投入）
                 └─ chunk → embedding → 向量库 → 检索 → 拼 prompt 全链路

第 5 周          阶段四 Redis 三场景 + 索引优化 + 阶段五 工具调用可视化
                 ├─ 缓存 / AI 限流 / 分布式锁
                 └─ 前端展示 Agent 每步调用（demo 加分）

第 6 周          阶段六 可观测性 + 压测 + 阶段八 MQ 异步
                 ├─ traceId 全链路 + Prometheus/Grafana
                 └─ AI 任务异步化，一份 QPS 报告

第 7 周（缓冲）  阶段七 多人协同（若时间充裕）+ 整体联调 + 简历打磨
                 └─ 面试话术演练、demo 录屏、README 完善
```

**关键排期原则**：
1. **阶段〇 最先做**：它是「修 bug」，2-3 天见效，先把现有污染问题解决，心里踏实。
2. **阶段九（部署）尽早首跑**：第 3 周就把 demo 部署上云，别拖到最后——部署踩坑最耗时，早暴露早解决，之后每个阶段增量部署。
3. **阶段三 RAG 单独给足一周**：它是本项目最大空白也是 AI 岗必问，别压缩。
4. **阶段七 多人协同放最后**：技术难度最高、最容易超时，作为「时间够就做」的亮点，不做也不影响主线。

---

## 3. 阶段〇：消费链路修复（P0，最先做）

**目标**：修复 `AiWritingService.generateStream` 不区分 author 导致的结果污染。

**这一步的详细方案已在 `project-expansion-plan-for-recruitment-2026-07-08.md` 第 2.4 节完整给出**，此处只列施工要点，不重复：

- 后端：加 `mapPhase(author)`，只有 `agent_writing_reviewer` 的输出进 `responseBuilder`，analyst/generator 仅用于过程展示。
- 前端：`StreamEvent.phase` 扩展为 `analyzing/generating/reviewing`，`AiWritingPanel` 按阶段展示进度条。
- 验证：先 log 确认 author name 与 yml 一致 → 跑通三阶段 → 确认落库只含终稿 → 清 log。

**交付物**：写作结果干净可采纳 + 一个「分析→生成→审查」阶段进度条（这就是免费得到的第一个 demo 亮点）。

**面试话术**：见 2.4.5 节。

---

## 4. 阶段一：真实用户体系 + JWT/Spring Security

**目标**：去掉 `DEFAULT_USER_ID = 1L`，建立真实登录鉴权，为多人协同/限流/数据隔离铺路。

> 产品层面的接口设计、前端改造 `next-development-roadmap` 第 3 节已详述，本节聚焦**安全深度**。

### 4.1 后端结构（贴合现有 DDD）
```text
domain.account
├── model/entity/UserEntity.java
├── adapter/repository/IUserRepository.java
└── service/UserDomainService.java   (login / queryCurrentUser / register)
infrastructure
├── dao/IUserDao.java + dao/po/UserPO.java
└── adapter/repository/UserRepository.java
trigger.http
├── AuthController.java   (POST /auth/login, /auth/register)
└── UserController.java   (GET /users/current)
```
`user` 表 DDL 已存在（`uk_username` 唯一索引），直接用。

### 4.2 安全实现（面试重点，做透）
- **Spring Security 过滤器链**：自定义 `JwtAuthenticationFilter` 挂在 `UsernamePasswordAuthenticationFilter` 前，解析 `Authorization: Bearer <token>`。
- **JWT**：pom 里已有 `jjwt` 和 `java-jwt`，二选一。签发含 `userId/username/exp`；讲清「无状态鉴权 vs Session」「双 token（access + refresh）续签」。
- **密码**：`BCryptPasswordEncoder` 加盐哈希，绝不明文。
- **越权防护**：现有 `validateOwner` 升级为统一切面/工具，讲「水平越权（改 ID 访问他人草稿）」防护。
- **当前用户获取**：`SecurityContextHolder` 或自定义 `@CurrentUser` 参数解析器，替换所有 `DEFAULT_USER_ID`。

### 4.3 交付与验收
- 全部 Controller 无 `DEFAULT_USER_ID`；草稿/文章按登录用户隔离；刷新后登录态恢复；他人资源返回 403。

### 4.4 面试话术
> 「用户鉴权走 Spring Security 过滤器链 + JWT 无状态方案，access token 短时效 + refresh token 续签，密码 BCrypt 加盐。资源接口做了归属校验防水平越权。这套是后续多人协同和 AI 接口限流的身份基础。」

<!-- PLACEHOLDER_2 -->

---

## 5. 阶段二：多 Agent 写作流水线 + draw.io 配图嵌入

**目标**：把当前「单条 sequential 三 Agent」升级为「规划 → 写作 → 配图 → 质检」四阶段可编排流水线，并把 draw.io 的三 Agent 子流程作为**子工作流**嵌入。

### 5.1 现状可复用的基建
- 已有 `SequentialAgentNode / ParallelAgentNode / LoopAgentNode` 三种编排节点。
- 已有 draw.io 的 `sequential_draw_process`（analyst→drawer→reviewer），配置在 `agent-draw-io.yml`。
- 写作侧 `agent-writing.yml` 已有三 Agent，阶段〇 已让消费端 author 感知。

### 5.2 改造思路：新增「配图 Agent」+ 工作流嵌套
在 `agent-writing.yml` 的 workflow 上做扩展：

```yaml
agents:
  # 已有 analyst / generator / reviewer，新增：
  - name: agent_writing_illustrator
    description: 识别正文中适合配图的段落，产出「需要画什么图」的绘图需求描述。
    instruction: |
      分析 {draft_content}，找出适合用架构图/流程图/时序图表达的段落。
      对每个需要配图的点，输出一行绘图需求 JSON：
      {"type":"illustration_request","anchor":"段落定位","diagramType":"architecture|flowchart|sequence","requirement":"具体画什么"}
      若无需配图，输出 {"type":"illustration_request","none":true}
    output-key: illustration_requests

agent-workflows:
  # draw.io 已有的子流程（保持不变，作为可复用子工作流）
  - type: sequential
    name: sequential_draw_process
    sub-agents: [agent_analyst, agent_drawer, agent_reviewer]

  # 新的写作主流水线，把配图作为其中一环
  - type: sequential
    name: sequential_writing_pipeline
    sub-agents:
      - agent_writing_analyst      # 规划
      - agent_writing_generator    # 写作（内部可换成 loop 逐章）
      - agent_writing_illustrator  # 配图需求识别
      - agent_writing_reviewer     # 质检
```

### 5.3 两种嵌入 draw.io 的方式（二选一）

**方式 A（推荐，工程更清晰）：应用层编排**
`agent_writing_illustrator` 只产出「绘图需求」，由 `AiWritingService` 在消费到 `illustration_request` 事件时，**用 draw.io 的 agentId(300000) 再发起一次子会话**，把绘图需求喂给 `sequential_draw_process`，拿到 `drawio_done` 的 XML 后插回文章对应锚点。
- 优点：两条 Agent 链解耦，draw.io 链零改动；能讲「工作流编排在应用层组合」。
- 面试点：**工作流可组合/可复用**——同一个 draw.io 子流程既服务画图页，也服务写作配图。

**方式 B（更「Agent 原生」）：ADK 子 Agent 嵌套**
若 ADK 支持将一个 workflow 作为另一个 workflow 的 sub-agent 节点，直接在 `sequential_writing_pipeline` 里把 `sequential_draw_process` 当一个节点挂进去。
- 优点：纯声明式，编排全在 yml。
- 风险：需确认 ADK 版本是否支持 workflow 嵌套；不确定就先用方式 A。

> **建议**：先做方式 A（确定可行、解耦、好讲），行有余力再探索方式 B。

### 5.4 交付与验收
- 提交「续写正文」→ 流水线依次经过 规划/写作/配图/质检 四阶段（前端进度条可见）。
- 正文中被识别的段落旁自动插入 draw.io 图（复用画图链路产出的 XML）。
- 最终落库内容 = 质检终稿 + 内嵌图。

### 5.5 面试话术
> 「写作是一条四阶段 Agent 流水线：规划、写作、配图、质检。配图环节我没有重写画图能力，而是把已有的 draw.io 三 Agent 子工作流（分析→绘图→审查）作为子流程复用——写作流水线识别出配图需求后，用绘图子工作流生成结构化 XML 再插回文章。这体现了工作流的可组合和可复用，一套 draw.io 编排同时服务画图页和文章配图两个场景。」

---

## 6. 阶段三：RAG 检索增强（本项目最大空白，重点投入）

**目标**：让写作 Agent 在生成前先检索「用户历史文章 / 技术知识库」，降低幻觉、提升与用户既有内容的一致性。你问的「以什么方式加入现有项目」——下面给出与现有 ADK/Armory 体系贴合的接入路径。

### 6.1 与现有架构的接入点
你的 `agent-writing.yml` 已经配了 `ai-api` 的 `embeddings-path`（`embeddings`），说明 embedding 能力的配置位已预留。RAG 接入分三处落地：

```text
① 数据侧（infrastructure）：新增向量存储
   infrastructure/rag/
   ├── VectorStoreClient.java        (封装向量库读写)
   ├── EmbeddingClient.java          (调用 embeddings-path 做向量化)
   └── DocumentChunker.java          (文章切分)

② 领域侧（domain.agent）：新增检索服务，作为 Agent 的一个「工具/前置步骤」
   domain/agent/service/rag/
   └── RagRetrievalService.java      (chunk→embed→topK 检索)

③ 编排侧：两种接入方式（见 6.3）
```

### 6.2 技术选型（结合你已有环境）
- **向量库**：你 docker-compose 里已有 MySQL 8 + Redis。**推荐加 PostgreSQL + pgvector**（部署一个容器即可，Spring AI 原生支持 `PgVectorStore`），比自建 Milvus 轻，面试也够讲。进阶想讲向量库选型再上 Milvus/Qdrant。
- **Embedding 模型**：复用 `agent-writing.yml` 里 `ai-api` 的 embeddings 接口（DeepSeek/兼容 OpenAI 格式），无需额外接入。
- **框架**：Spring AI 的 `VectorStore` + `EmbeddingModel` 抽象，与你已用的 Spring AI 一致。

### 6.3 两种接入 Agent 的方式
**方式 A（推荐先做）：作为写作流水线的前置检索步骤**
在 `agent_writing_analyst` 之前（或之内），由 `AiWritingService` 先调 `RagRetrievalService.retrieve(draft内容, topK)`，把检索到的片段拼进 analyst 的输入 prompt。
- 优点：链路直观，检索结果对全流水线可见；不依赖 Agent 自主决策。

**方式 B（进阶）：把检索封装成 MCP 工具，让 Agent 自主调用**
把 `RagRetrievalService` 包装成一个本地 MCP tool（你已有 `LocalToolMcpCreateService`），挂到 `tool-mcp-list`，让 Agent 自己决定「要不要查、查什么」。
- 优点：更「Agent 原生」，能讲「Agent 自主工具调用 + RAG」；和你已有的 baidu-search MCP 是同一套机制。
- 面试点：**RAG 的两种范式**——固定检索 vs Agent 自主检索（Agentic RAG），你能对比着讲，这是加分项。

### 6.4 完整链路与面试点
```text
建库：用户历史文章 -> DocumentChunker 切分 -> EmbeddingClient 向量化 -> 存 pgvector
检索：写作请求 -> query 向量化 -> pgvector topK 相似检索 -> 重排/过滤 -> 拼进 prompt -> Agent 生成
```
- chunk 策略（定长 vs 语义切分、overlap）、embedding 维度、召回 vs 精确、如何过滤低相关片段、如何评估（人工标注小样本看命中率）。

### 6.5 交付与验收
- 写一篇与用户历史文章主题相关的文章时，能看到 Agent 引用了历史内容的术语/结论。
- 关掉 RAG 对比开着 RAG 的输出差异（demo 时很有说服力）。

### 6.6 面试话术
> 「为降低幻觉，我加了 RAG：把用户历史文章切分、embedding 后存进 pgvector，写作前检索 topK 相关片段拼进 prompt。我实现了两种接入——固定前置检索，以及把检索封装成 MCP 工具让 Agent 自主决定是否调用（Agentic RAG），后者复用了我已有的本地 MCP 工具机制。」

<!-- PLACEHOLDER_3 -->

---

## 7. 阶段四：Redis 三场景 + 数据库索引优化

**目标**：让中间件在项目里有真实业务动机的落地，经得起八股追问。

### 7.1 Redis 三场景（都选「有业务动机」的）
| 场景 | 落地点 | 面试考点 |
|------|--------|---------|
| **AI 接口限流** | AI 写作/画图接口按用户令牌桶限流（LLM 调用贵，动机真实） | 令牌桶 vs 滑动窗口、Redis + Lua 保证原子性 |
| **缓存** | 文章详情、热门文章列表 | 穿透（空值缓存/布隆）、击穿（互斥锁）、雪崩（随机 TTL）、双写一致性 |
| **分布式锁** | AI 任务防重复提交、同草稿并发编辑加锁 | Redisson 看门狗续期、锁误删（value 校验）、可重入 |

> 你 docker-compose 已有 redis:6.2，直接用。限流建议用 Redisson 的 `RRateLimiter` 或自写 Lua 令牌桶。

### 7.2 数据库索引优化
- 你的 DDL 已有较好的联合索引（`idx_user_status_update_time`、`idx_author_status_publish_time`），可直接讲「为什么这么建、最左前缀、覆盖索引」。
- 补一个**深分页优化**演示：`limit 100000,10` → 改子查询/游标（`where id > ? limit 10`），配 `explain` 前后对比。

### 7.3 面试话术
> 「Redis 落了三个有真实动机的场景：AI 接口用令牌桶限流（因为 LLM 调用有成本），文章详情做缓存并处理了穿透/击穿/雪崩，AI 任务提交用分布式锁防重复。索引上，文章表按 `author_id+status+publish_time` 建联合索引走最左前缀，深分页用游标改写，explain 从全表扫描优化到走索引。」

---

## 8. 阶段五：工具调用可视化（demo 加分）

**目标**：把 Agent 每一步「调用了什么工具、参数、返回、耗时」推给前端展示（类似 Claude/Cursor 的工具调用过程）。

- 后端：在 `chatService.handleMessageStream` 消费事件时，不再 `return` 跳过 `functionCalls/functionResponses`，而是把它们封装成 `tool_call` / `tool_result` 事件推给前端。
- 前端：在阶段进度条基础上，展开每个工具调用卡片（工具名、入参、返回摘要、耗时）。
- **这是面试现场最直观的「Agent 在干活」证据**，录一段 demo 视频放简历。

**面试话术**：
> 「我把 Agent 的工具调用过程做了可视化——每次调用 baidu-search 或 RAG 工具，前端能看到调用参数、返回和耗时。这既是调试手段，也让用户理解 Agent 的决策过程。」

---

## 9. 阶段六：可观测性（traceId 全链路）+ 压测

**目标**：从「能跑」到「可观测、有数据」。

### 9.1 traceId 全链路
- 用 MDC + 拦截器/过滤器，请求入口生成 traceId，贯穿 Controller → Service → Agent 调用 → SSE 事件。
- 一次 AI 写作请求各阶段（分析/生成/配图/质检）耗时用同一 traceId 串起来，日志可检索。
- 进阶：Micrometer + Zipkin/SkyWalking 做可视化链路。

### 9.2 监控 + 压测
- Prometheus + Grafana：QPS、RT、AI 调用成功率、token 消耗。
- 压测：JMeter/wrk 压核心接口（文章列表、AI 提交），**简历写出具体单机 QPS 数字**。
- 注意 AI 接口本身慢（依赖 LLM），压测重点放在非 AI 的 CRUD 接口 + 限流是否生效。

### 9.3 面试话术
> 「一次 AI 请求用 traceId 贯穿从 Controller 到四个 Agent 阶段，各阶段耗时可追。用 Prometheus+Grafana 监控 QPS/RT/AI 成功率/token 消耗，JMeter 压测非 AI 接口单机 QPS 达 XXX，并验证了限流生效。」

---

## 10. 阶段七：多人在线协同编辑（P2 亮点）

**目标**：支持多人实时协作编辑同一草稿（多光标、实时同步），这是你新增的需求，也是全项目技术难度最高、最出彩的亮点。

### 10.1 技术选型
| 方案 | 原理 | 适合度 |
|------|------|--------|
| **CRDT（推荐）** | 无冲突复制数据类型，前端用 **Yjs**，后端做同步中继 | 主流、去中心冲突解决、掉线可合并 |
| OT（Operational Transformation） | 操作变换，Google Docs 早期方案 | 实现复杂，需中心服务器变换 |

> 推荐 **Yjs + WebSocket**：前端 Yjs 管理文档 CRDT 状态，后端用 WebSocket（Spring 的 `@ServerEndpoint` 或 Netty）做房间广播中继 + 持久化。

### 10.2 后端要做的
```text
trigger/websocket/
└── CollabEditWebSocketHandler.java   (按 draftId 分房间，广播 Yjs update)
domain/collab/
├── CollabSessionService.java         (在线用户、光标、房间管理)
└── 定时/防抖 把 CRDT 快照落库 draft 表
```
- **Redis 的新用武之地**：用 Redis Pub/Sub 做多实例间的房间消息广播（讲「WebSocket 水平扩展」），用 Redis 存在线用户/光标位置。
- 与阶段一鉴权结合：WebSocket 握手校验 JWT，只有草稿协作者能进房间。

### 10.3 与 AI 写作的结合点（差异化亮点）
- **多人 + AI 协同**：一个用户触发 AI 续写时，生成的内容通过协同通道实时同步给房间内其他人（「AI 也是房间里的一个协作者」）。这个点非常新颖，面试极出彩。

### 10.4 面试话术
> 「多人协同用 Yjs（CRDT）+ WebSocket，后端按草稿 ID 分房间广播增量，用 Redis Pub/Sub 支持多实例水平扩展，握手校验 JWT。特别地，AI 续写的结果也通过协同通道实时同步——AI 相当于房间里的一个协作者，多人能同时看到 AI 逐字生成。」

> 风险提示：这是最容易超时的阶段，务必在主线（阶段〇~六）稳定后再做。做不完就作为「已设计、部分实现」诚实陈述。

---

## 11. 阶段八：MQ 异步场景

**目标**：一个有真实动机的异步化场景。
- **AI 任务异步化**：提交任务进 MQ（RocketMQ/RabbitMQ），消费者调 Agent，结果经 SSE/WebSocket 推回。解决「同步阻塞 stream 占用连接」。
- **发布后异步统计**：文章发布后异步更新阅读量/作者文章计数/（未来）搜索索引。
- 面试点：可靠投递、幂等消费、削峰填谷、死信队列。
- **提醒**：MQ 是「讲一个场景即可」，不必铺开。

---

## 12. 阶段九：云服务器部署（P0，尽早首跑并贯穿全程）

**目标**：公网可访问的 demo，面试时直接甩链接/录屏。

### 12.1 选型与成本
- **服务器**：阿里云/腾讯云轻量应用服务器（2C4G 起，学生机便宜）。
- **域名**：备案可选（不备案用 IP + 端口也能 demo，但有域名更专业）。
- 你 docs 里已有 `docker-compose-environment.yml`（MySQL/Redis/phpMyAdmin）和 `docker-compose-app.yml`、`docker-compose-environment-aliyun.yml`——**部署基建已具雏形**。

### 12.2 部署架构
```text
Nginx (反向代理 + 前端静态资源 + SSL)
  ├── /            -> 前端 Next.js (build 后静态导出 或 node 服务)
  ├── /api/v1/     -> 后端 Spring Boot (Docker)
  └── /ws/         -> WebSocket (多人协同)
后端容器 ── MySQL / Redis / pgvector(RAG) 容器
```

### 12.3 步骤与时间线建议
```text
第 3 周首次部署（最小集）：Nginx + 后端 + MySQL + Redis，跑通登录和 AI 写作
之后每个阶段增量部署：RAG 加 pgvector 容器、协同加 WS 端口
上线前：HTTPS(Let's Encrypt)、环境变量管理 API Key(别硬编码进镜像)、日志挂载
```

### 12.4 安全 checklist（网络暴露必看）
- API Key（DeepSeek/baidu-search）用环境变量或密钥管理，**绝不进 git/镜像**。
- 数据库/Redis 不暴露公网端口，只在内网 docker network。
- 后端接口全部经 JWT 鉴权（阶段一已做）；AI 接口限流已做（阶段四），防止被刷爆 token 成本。
- Nginx 加基础限流和 CORS 收敛。

### 12.5 面试话术
> 「项目用 Docker Compose 编排 Nginx+前后端+MySQL+Redis+pgvector 部署在云服务器，Nginx 反代 + HTTPS，API Key 走环境变量不进镜像，中间件不暴露公网，AI 接口有限流防刷。可以直接在线演示。」

---

## 13. 总结：两条主线与最终简历描述

### 13.1 双核主线
- **AI Agent 差异化线**：阶段〇（消费修复）→ 二（多 Agent 流水线+配图嵌入）→ 三（RAG）→ 五（工具可视化）→ 七（多人+AI 协同）。
- **Java 后端工程线**：阶段一（用户+安全）→ 四（Redis+索引）→ 六（可观测+压测）→ 八（MQ）→ 九（部署）。

### 13.2 一句话简历描述（施工完成后填数字）
> **AI Agent 技术写作平台**：基于 Spring Boot + DDD + Google ADK/Spring AI，构建规划-写作-配图-质检四阶段多 Agent 写作流水线，复用 draw.io 三 Agent 子工作流实现文章自动配图；引入 pgvector RAG（含 Agentic RAG）降低幻觉；Spring Security + JWT 鉴权与越权防护；Redis 实现 AI 接口限流/缓存/分布式锁，单机压测 QPS 达 XXX；支持基于 Yjs+WebSocket 的多人实时协同编辑；traceId 全链路追踪 + Prometheus 监控；Docker Compose 云服务器部署，公网可演示。

### 13.3 执行纪律（反复提醒自己）
1. **深度 > 数量**：每个阶段做到「能被追问 5 分钟」，别贪多。
2. **尽早部署**：第 3 周必须让 demo 上云，别把部署坑留到最后。
3. **诚实边界**：分清「已落地」和「已设计」，面试不吹没做的。
4. **留缓冲周**：第 7 周是缓冲 + 打磨，不要排满。

---

*本文档为施工总纲，与 `project-expansion-plan-for-recruitment-2026-07-08.md`（方向与话术）、`next-development-roadmap-2026-07-08.md`（产品功能细节）配合使用。各阶段动手前，回查对应文档的详细设计。*



