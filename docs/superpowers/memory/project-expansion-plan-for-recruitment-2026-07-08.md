# 项目扩展方案（面向 2026 秋招）— 2026-07-08

> 本文档面向的目标读者：**一名计算机专业研二学生，2026 年 8 月开始秋招，主投 Java 后端 / AI Agent 研发岗**。
> 因此本方案与已有的 `next-development-roadmap-2026-07-08.md` 视角不同：
> - 已有 roadmap 关注「产品完整度」（用户体系、社区、编辑体验）。
> - **本文档关注「简历与面试价值」**：每个方向都回答一个问题——「做了它，面试时能讲出什么别人讲不出的东西？」
>
> 两份文档不冲突，建议配合使用：产品 roadmap 决定「做什么功能」，本方案决定「优先补哪些技术深度，怎么在简历和面试里讲」。

---

## 0. 先回答你的两个核心疑惑

### 疑惑一：AI 已经能写文章了，为什么还要套一层 Agent？是不是多此一举？

这是一个非常好的问题，而且**它恰恰会是面试官问你的第一个问题**。如果你答不好，这个项目就是「调了个大模型 API 的博客」；答得好，它就是「Agent 工程化实践」。

区别在于：**「调用大模型」和「Agent」是两个层次的东西。**

| 维度 | 直接调 LLM（Chat 补全） | Agent 架构 |
|------|------------------------|-----------|
| 能力边界 | 只能基于训练知识 + 你给的 prompt 生成文本 | 能**调用工具/联网检索**（你项目里 baidu-search MCP）、按规范产出结构化数据（draw.io XML / PPT JSON） |
| 上下文 | 单轮，无状态 | 有 session、有记忆、有多轮规划 |
| 任务复杂度 | 一问一答 | 可拆解任务、串行/并行/循环编排（你项目 draw.io 已真实跑通 `analyst→drawer→reviewer` 串行工作流） |
| 可扩展性 | 加功能就是改 prompt | 加能力 = 注册新 Agent / 挂新 MCP / 加 skill 规范 |

你项目里真正体现 Agent 价值的地方，是这条**已经落地**的链路：draw.io 画图不是「一个 prompt 出一张图」，而是**「需求分析 Agent → 绘图 Agent（逐节点输出结构化 XML）→ 审查 Agent（检查布局重叠、连线合法性）」的三 Agent 串行工作流**，中间 `agent_analyst` 还会调 baidu-search MCP 联网补充资料，绘图时按 `drawio-architecture` 等 skill 规范生成节点样式。纯 LLM 做不到这种「多角色分工 + 联网检索 + 按工程规范产出可渲染结构化数据 + 自我审查」的协同。

**所以结论：如果你的项目只是「输入标题→输出文章」，那 Agent 确实是多余的。它的价值必须靠「工具调用 + 多步编排」撑起来。** 这也直接决定了你下面最该补的方向（见方向一）。

一句话面试话术：
> 「我没有停留在调用 LLM 补全，而是基于 Google ADK 搭了一套 Agent 装配（Armory）体系，用策略树模式动态装配 Agent，支持 Sequential/Parallel/Loop 三种工作流编排、SSE MCP 工具接入(联网检索)和 Skills 规范注入。draw.io 画图场景就是一条真实的三 Agent 串行工作流，Agent 分工完成需求分析、结构化 XML 生成和布局审查，这是纯 prompt 方案做不到的。」

### 疑惑二：这个项目怎么样？

**基础很好，但当前「深度」还不足以直接打硬仗。** 客观评价：

优点（已经做对的事）：
- **架构规范**：DDD 四层 + 多 Maven 模块（api / domain / trigger / infrastructure / types），这在校招项目里是加分项，说明你懂分层和依赖倒置。
- **有 Agent 硬核**：接了 Google ADK、Spring AI、MCP、workflow 节点，`Armory` 用了策略树模式做装配。这是差异化。
- **有完整闭环**：草稿 → AI 辅助 → 发布 → 展示，是个能演示的东西。

短板（面试会被戳穿的地方）：
- **单机、无并发压力**：目前 `DEFAULT_USER_ID = 1L`，没有真实用户，没有高并发场景，讲不出「扛住了多少 QPS」。
- **无中间件深度**：Redis 只在 pom 里，没看到缓存/分布式锁/限流的真实使用；没有消息队列。
- **Agent 深度偏「搭好了框架」而非「用出了效果」**：多 Agent 编排已在 draw.io/ppt/写作三个 agent 配置里定义并运行，但**写作链路 `AiWritingService.generateStream` 消费编排结果的逻辑是坏的**——它不区分 author，把分析/生成/审查三个 Agent 的输出无脑拼进一个 buffer 一起返回并落库，导致最终结果被中间过程污染（详见第 2.4 节）。
- **无可观测性 / 无压测数据 / 无部署闭环**。

**这些短板正好就是下面的扩展方向。** 补齐 2-3 个，这个项目就能从「课程设计级」升到「能进大厂终面被追问」级。

---
## 1. 扩展方向总览（按「面试价值 / 投入产出比」排序）

下面 6 个方向，我按「秋招性价比」从高到低排。**你不需要全做，选 3-4 个做深，远好于 6 个都浅尝。**

| # | 方向 | 面试价值 | 工作量 | 推荐度 | 主要对应岗位 |
|---|------|---------|--------|--------|-------------|
| 一 | **Agent 能力做深（工具+编排+RAG）** | ★★★★★ | 中 | 必做 | AI/Agent 研发、大模型应用 |
| 二 | **中间件与高并发深度（Redis/MQ/限流）** | ★★★★★ | 中 | 必做 | Java 后端 |
| 三 | **用户体系 + 安全（JWT/Spring Security/幂等）** | ★★★★ | 低 | 强烈推荐 | Java 后端 |
| 四 | **可观测性与工程化（日志/链路/监控/CI）** | ★★★★ | 中 | 推荐 | 后端 / SRE 方向 |
| 五 | **社区能力 + 复杂查询优化（点赞/收藏/Feed）** | ★★★ | 中 | 可选 | Java 后端 |
| 六 | **部署与云原生（Docker/K8s/压测）** | ★★★ | 中高 | 加分项 | 后端 / 运维 |

> 关键判断：**方向一 + 方向二是这个项目的两条命脉。** 方向一让它区别于普通 CRUD 博客（AI 岗看点），方向二让它经得起 Java 后端八股追问（后端岗看点）。这两个是你简历上这个项目的「双核」。

---

## 2. 方向一：把 Agent 能力做深（最高优先级）

> **目标**：让面试官相信你「真的做过 Agent 工程」，而不是「调过 API」。

### 2.1 当前状态诊断（2026-07-08 复核更正）

> **重要更正**：经查证 `agent-draw-io.yml` / `agent-ppt.yml` / `AgentServiceController`，此前两处描述有误，特此更正：
> 1. **draw.io / PPT 不是 MCP 工具**，而是**多 Agent 工作流 + 结构化 JSON 流式输出 + 前端渲染**。
>    - draw.io 走 Sequential 编排：`agent_analyst`(需求分析) → `agent_drawer`(逐节点生成 draw.io XML) → `agent_reviewer`(布局检查)；PPT 同理(analyst → generator → reviewer)。
>    - Agent 按行输出 `{"type":"drawio_node",...}` / `drawio_edge` / `drawio_done` 结构化 JSON，后端 `processAndSendLine` 逐行解析转发，前端用 `react-drawio` / `pptxgenjs` 渲染。图是「Agent 生成 XML、前端画」，不是工具画的。
>    - 项目里真正的 MCP 是 **`baidu-search`**(SSE，联网检索)；draw.io 画图规范走的是 **skills**(`drawio-uml`/`drawio-sequence`/`drawio-architecture`/`drawio-flowchart`，resource 类型)，也非 MCP。
> 2. **多 Agent 编排不是空架子，已真实落地**：draw.io/ppt 场景真跑了 Sequential/Loop/Parallel 工作流。只有「文章写作」模块(`AiWritingService.generateStream`)仍是单 Agent 单轮。

修正后的状态：
- 已有且**已真实使用**：ADK 装配(Armory 策略树)、Sequential/Loop/Parallel 多 Agent 编排(draw.io/ppt)、SSE MCP(baidu-search)、Skills 机制、结构化流式输出。
- 已有但**未在业务用起**：Local/Stdio MCP 客户端、写作模块的多 Agent 编排。
- 完全缺失：**RAG 检索增强**、工具调用可视化、成本/token 治理。

因此本方向的重点从「从 0 搭编排」调整为：**① 把 draw.io 已验证的多 Agent 模式复制到写作模块；② 补齐 RAG 这个最大空白。**

### 2.2 建议做的四件事

#### （1）真正跑通一条「多 Agent 编排」的写作流水线 —— 最重要
把「写一篇技术文章」变成一条可编排的流水线，真实使用你已有的 `SequentialAgentNode`：

```text
【规划 Agent】拆解主题 -> 输出大纲
    ↓ (Sequential)
【写作 Agent】逐章续写正文（可 Loop：每章一次）
    ↓
【配图 Agent】识别需要图示的段落 -> 复用 draw.io 工作流（analyst->drawer->reviewer）产出结构化图表 JSON
    ↓
【质检 Agent】检查技术准确性/结构完整性 -> 输出结构化报告
```

> 注意：配图不是「调 MCP 工具」，而是**把已有的 draw.io 三 Agent 子流程作为一个子工作流嵌进写作流水线**——这正好体现「工作流可组合/嵌套」，比单纯调工具更能讲出编排深度。

面试话术升级为：
> 「文章生成是一条多阶段的 Agent 流水线，规划、写作、配图、质检各是独立 Agent，用 Sequential 编排串起来，写作阶段内部可用 Loop 逐章生成。配图阶段直接复用我已有的 draw.io 子工作流（分析→绘图→审查三个 Agent），Agent 按行输出结构化 draw.io XML，前端流式渲染成图。整套是多 Agent 协同 + 工作流嵌套。」

#### （2）加 RAG（检索增强）—— AI 岗几乎必问
现状：Agent 写作只靠模型内部知识，容易「一本正经胡说」。
建议：引入向量检索，让写作 Agent 先检索「用户历史文章 / 知识库」再写。

- 技术选型（二选一，都能讲）：
  - 轻量：Spring AI 自带的 `VectorStore` + PostgreSQL `pgvector`（部署简单）。
  - 进阶：接 Milvus / Qdrant（能讲向量数据库选型）。
- 链路：`文档切分(chunking) -> 向量化(embedding) -> 存向量库 -> 检索 topK -> 拼进 prompt`。
- 面试点：chunk 策略、embedding 模型选择、召回率 vs 精确率、如何解决「检索到不相关内容」。

#### （3）工具调用可视化 + 失败重试
- 把 Agent 每一步「调用了什么工具、参数、返回」通过 SSE 推给前端展示（类似 Claude/Cursor 的「工具调用过程」）。这是**极强的演示效果**，面试现场 demo 一放，印象分拉满。
- 工具调用失败时的重试 / 降级策略（讲容错）。

#### （4）Prompt 工程与成本控制
- 抽出 prompt 模板管理（现在硬编码在 `buildPrompt` 的 switch 里，可讲「为什么要模板化」）。
- Token 用量统计与成本估算（讲「LLM 应用的成本意识」，这是很多候选人没有的加分点）。
- 加缓存：相同输入命中缓存不重复调模型（和方向二的 Redis 结合）。

### 2.3 这个方向能撑起的面试问题
- Agent 和直接调 LLM 的区别？（你已能答）
- MCP 协议是什么？为什么用它而不是自己定义工具接口？
- 多 Agent 怎么编排？串行/并行/循环各适合什么场景？
- RAG 的完整链路？如何评估检索质量？
- 如何防止 LLM 幻觉？如何控制成本？
- Function Calling 的原理？

### 2.4 落地方案：把 draw.io 的消费方式复制到写作主链路

> 这是**最高性价比、可立即动手**的一件事。它同时是「修 bug」+「拿讲点」：现在写作结果被中间过程污染是真实缺陷，修好后又天然得到「多 Agent 分阶段可视化」这个面试硬通货。

#### 2.4.0 先看清两条链路的本质差异

两个场景**用的是同一套 ADK 多 Agent 编排**（都是 Sequential 三 Agent），区别只在「消费 stream 的代码怎么写」：

| | draw.io（`AgentServiceController.chatStream`） | 写作（`AiWritingService.generateStream`） |
|---|---|---|
| 后端消费入口 | `chatService.handleMessageStream(300000)` | `chatService.handleMessageStream(300002)` |
| 工作流 | `analyst → drawer → reviewer` | `agent_writing_analyst → agent_writing_generator → agent_writing_reviewer` |
| **是否区分 `event.author()`** | ✅ 区分：按 author 映射 phase、按 author 分 buffer、只透传最终 Agent 的结构化结果 | ❌ **不区分**：所有 author 的 content 全部 `responseBuilder.append` |
| 结果正确性 | 正确：前端只渲染 reviewer 的终图，过程只当状态展示 | **错误**：analyst 的分析文字 + generator 初稿 + reviewer 终稿被拼成一坨落库 |

**根因**：`generateStream` 里这段（`AiWritingService.java:82-93`）——

```java
events.blockingForEach(event -> {
    if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) return;
    String content = event.stringifyContent();
    if (null == content || content.isBlank()) return;
    responseBuilder.append(content);              // ← 三个 Agent 的输出全 append 进最终结果
    eventConsumer.accept(tokenEvent(content));    // ← 全部当 token 推给前端，phase 恒为 generating
});
```

而 draw.io 的 `AgentServiceController` 用一个 `switch(author)` 把 `agent_analyst / agent_drawer / agent_reviewer` 分别映射成 `analyzing / drawing / reviewing` 阶段——**我们要把这套「author 感知」搬到写作链路**。

#### 2.4.1 目标行为

改造后 `generateStream` 的语义应为：

```text
agent_writing_analyst  (output-key: writing_analysis) -> phase=analyzing -> 仅推 status 给前端展示「正在分析草稿…」，不进最终结果
agent_writing_generator(output-key: draft_content)    -> phase=generating -> 推 token 给前端做实时预览，但不作为最终落库内容
agent_writing_reviewer (output-key: final_result)     -> phase=reviewing  -> 这是终稿，token 推前端 + append 进 responseBuilder
最终落库 responseContent = 仅 reviewer 的终稿
```

关键点：**只有 `agent_writing_reviewer` 的输出才进 `responseBuilder`**，其余 Agent 的输出只用于「过程可视化」。这与 draw.io「只把 reviewer 的最终结构化结果透传给前端渲染」的思路完全一致。

#### 2.4.2 后端改造（核心，约 30 行）

**第 1 步：加一个 author → phase 的映射方法**（照抄 `AgentServiceController` 的 switch 思路）

```java
// AiWritingService.java —— 与 yml 中 agents 的 name 严格对齐
private static final String AUTHOR_ANALYST   = "agent_writing_analyst";
private static final String AUTHOR_GENERATOR = "agent_writing_generator";
private static final String AUTHOR_REVIEWER  = "agent_writing_reviewer";

private String mapPhase(String author) {
    if (null == author) return "thinking";
    return switch (author) {
        case AUTHOR_ANALYST   -> "analyzing";
        case AUTHOR_GENERATOR -> "generating";
        case AUTHOR_REVIEWER  -> "reviewing";
        default -> "thinking";
    };
}
```

> 前置确认：`event.author()` 在 ADK 事件里返回的就是 yml 里配置的 agent `name`（draw.io 的 `AgentServiceController` 正是这么用的，可直接参照验证）。改造前先打一行 `log.info("author={}", event.author())` 跑一次，确认三个 name 拼写与 yml 完全一致，这是整个改造的地基。

**第 2 步：改写 `generateStream` 的事件消费逻辑**

```java
StringBuilder finalBuilder = new StringBuilder();   // 只装 reviewer 终稿
StringBuilder previewBuilder = new StringBuilder();  // generator 预览（可选落库/可选丢弃）

events.blockingForEach(event -> {
    if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) return;
    String content = event.stringifyContent();
    if (null == content || content.isBlank()) return;

    String author = event.author();
    String phase = mapPhase(author);

    if (AUTHOR_ANALYST.equals(author)) {
        // 分析阶段：不进任何结果，只推一个「阶段状态」让前端展示进度
        eventConsumer.accept(buildEvent(phase, "status", "正在分析草稿与写作意图…"));
        return;
    }

    if (AUTHOR_GENERATOR.equals(author)) {
        // 生成阶段：推 token 给前端做「初稿预览」，但不作为最终结果
        previewBuilder.append(content);
        eventConsumer.accept(buildEvent(phase, "token", content));
        return;
    }

    if (AUTHOR_REVIEWER.equals(author)) {
        // 审查阶段：这是最终结果，既推前端也进最终 buffer
        finalBuilder.append(content);
        eventConsumer.accept(buildEvent(phase, "token", content));
        return;
    }

    // 兜底：未知 author 当思考过程，仅展示不落库
    eventConsumer.accept(buildEvent(phase, "status", content));
});

// 落库只用 reviewer 终稿；若 reviewer 意外无输出，降级用 generator 预览兜底
String finalContent = finalBuilder.length() > 0 ? finalBuilder.toString() : previewBuilder.toString();
markSuccess(task, finalContent);
eventConsumer.accept(doneEvent());
```

**第 3 步：扩展 `buildEvent` 支持新的 phase 值**
现有 `buildEvent(phase, type, content)` 已经是通用的，无需改造，只要传入 `analyzing / generating / reviewing` 即可。`statusEvent/tokenEvent` 这些便捷方法可保留，也可逐步用 `buildEvent` 替代。

> **重要提醒（关于前端预览体验）**：generator 先吐一版初稿、reviewer 再吐一版终稿，前端如果两版都 append 到同一个 buffer，会出现「文章生成两遍」的观感。有两种处理：
> - **方案 A（推荐，简单）**：前端在收到第一个 `reviewing` 阶段的 token 时，清空 generator 阶段的预览 buffer，改为累积 reviewer 内容。即「预览用 generator，定稿切 reviewer」。
> - **方案 B（更省 token）**：如果 reviewer 的 prompt 是「输出完整终稿」，可以让后端干脆不把 generator 的 token 推给前端（只推 `analyzing`/`reviewing` 两个阶段的状态与终稿），generator 阶段只显示「正在生成初稿…」状态条。体验更干净，但用户看不到逐字生成过程。
>
> 建议先做方案 A，演示效果最好（能看到「分析→生成→润色定稿」全过程）。

#### 2.4.3 前端改造（对齐 draw.io 的阶段展示）

**第 1 步：扩展 `types/ai-writing.ts` 的 phase 枚举**

```ts
export interface StreamEvent {
  // 从 'thinking' | 'generating' | 'done' | 'error'
  // 扩展为区分三个 Agent 阶段
  phase: 'thinking' | 'analyzing' | 'generating' | 'reviewing' | 'done' | 'error';
  chunk: StreamChunk;
}
```

**第 2 步：`AiWritingPanel` 按 phase 分阶段展示**（借鉴 draw.io 页面的 `MessageStep[]` 模式）

现在的消费逻辑（`AiWritingPanel/index.tsx:94-98`）只认 `chunk.type`，不认 `phase`。改造为：

```tsx
const controller = await aiWritingApi.streamTask(
  taskId,
  (event) => {
    const { phase, chunk } = event;
    // 用 phase 驱动「步骤条」展示：分析中 / 生成中 / 审查定稿中
    if (phase === "analyzing") setAiStageLabel("分析草稿与写作意图…");
    if (phase === "generating") setAiStageLabel("生成初稿…");
    if (phase === "reviewing")  setAiStageLabel("润色审查，输出终稿…");

    if (chunk.type === "status") setAiStatusMessage(chunk.content);
    if (chunk.type === "token") {
      // 方案 A：进入 reviewing 阶段时清空预览，改为累积终稿
      if (phase === "reviewing") {
        setAiResultBuffer((prev) => (reviewStartedRef.current ? prev + chunk.content : chunk.content));
        reviewStartedRef.current = true;
      } else {
        setAiResultBuffer((prev) => prev + chunk.content); // generator 预览
      }
    }
    if (chunk.type === "done") { setAiStatusMessage("生成完成"); setAiTaskStatus("done"); }
    if (chunk.type === "error") { setAiStatusMessage(chunk.content || "生成失败"); setAiTaskStatus("error"); }
  },
  // ...onError / onComplete 不变
);
```

其中 `reviewStartedRef` 用 `useRef(false)` 定义，每次提交任务前重置为 `false`。

**第 3 步（可选，演示加分）**：在面板顶部加一条「阶段进度条」，把 `analyzing → generating → reviewing → done` 四步用小圆点+文字展示，完全对齐 draw.io 页面的观感。这一步纯 UI，但**面试 demo 时是最直观的「多 Agent 协同」证据**。

#### 2.4.4 验证清单

改完后按这个顺序验证：

```text
1. 后端加临时 log 打印 event.author()，确认三个 author name 与 yml 完全一致
2. 提交一次「续写正文」任务，观察 SSE 事件流的 phase 依次出现 analyzing -> generating -> reviewing
3. 确认最终落库的 ai_task.response_content 只包含 reviewer 终稿，不含分析文字/初稿
4. 前端确认：预览过程流畅，最终展示的是终稿而非「生成两遍」
5. 采纳结果到草稿，确认插入内容干净、可直接使用
6. 异常场景：reviewer 无输出时，降级用 generator 预览兜底，任务不报错
7. 移除临时 log
```

#### 2.4.5 这次改造的面试话术

> 「写作用的是三 Agent 串行工作流：分析师梳理写作意图和文章结构、生成器产出初稿、审查员做技术准确性和 Markdown 规范的终校。我在消费 ADK 事件流时按 `author` 区分阶段——分析阶段只做进度展示，生成阶段推初稿预览，只有审查员的终稿才作为最终结果落库，避免中间过程污染结果。前端用阶段进度条把这套多 Agent 协同过程可视化了。这套 author 感知的消费模式，我在 draw.io 画图链路和写作链路上是复用的。」

> 延伸追问准备：
> - 为什么不直接取最后一个 Agent 的整体输出？→ 因为是流式 SSE，要边生成边推前端做实时体验，不能等全部结束再取。
> - author 是怎么来的？→ ADK 事件携带产生该事件的 agent name，与 yml 配置的 `agents[].name` 对应。
> - 如果以后加第四个 Agent（如配图）怎么办？→ 只需在 `mapPhase` 加一个分支、前端加一个阶段，消费逻辑天然可扩展——这就是把「阶段判断」收敛到一处的好处。

---

## 3. 方向二：中间件与高并发深度（Java 后端命脉）

> **目标**：让这个项目经得起「你项目扛过多少并发」「用了哪些中间件解决什么问题」的追问。

### 3.1 Redis 用出真实场景（现在只在 pom 里）
按「面试常考 + 你项目能自然落地」排：

| 用法 | 在你项目里的落地点 | 面试考点 |
|------|-------------------|---------|
| **缓存** | 文章详情 / 热门文章列表缓存 | 缓存穿透/击穿/雪崩、双写一致性 |
| **分布式锁** | 同一草稿并发编辑、AI 任务防重复提交 | Redisson、看门狗续期、锁误删 |
| **限流** | AI 生成接口限流（LLM 调用很贵！） | 令牌桶/滑动窗口、Redis+Lua 原子性 |
| **计数** | 文章阅读量 / 点赞数（先写 Redis 再异步落库） | 缓存与 DB 一致性、原子递增 |
| **会话** | 登录 token 存 Redis | 分布式 session |

**特别推荐「AI 接口限流」**：因为 LLM 调用有真实成本，这是一个「有业务动机」的限流场景，比「为了限流而限流」讲起来自然得多。

### 3.2 引入消息队列（RocketMQ / RabbitMQ / Kafka）
现状：AI 写作是同步阻塞 stream。可以改造出异步场景：

- **场景**：AI 生成任务异步化 —— 提交任务进 MQ，消费者调用 Agent，结果通过 WebSocket/SSE 推回。
- **场景**：文章发布后，异步更新「阅读量统计」「作者文章计数」「搜索索引」。
- 面试考点：消息可靠投递、幂等消费、顺序消息、死信队列、削峰填谷。

> 提醒：MQ 是「加分但非必须」。如果时间紧，优先把 Redis 做透，MQ 讲一个场景即可。

### 3.3 数据库优化
- 分页查询优化（深分页问题：`limit 100000, 10` 怎么优化）。
- 索引设计（文章表按 `user_id + status + create_time` 建联合索引）。
- 慢查询分析（讲 `explain`）。
- 如果想更深：分库分表（ShardingSphere），但校招项目做这个略重，讲清楚「为什么现在不需要、什么量级才需要」反而更显成熟。

### 3.4 这个方向能撑起的面试问题
覆盖 Redis 全套八股、MQ 全套八股、MySQL 索引与优化——**这是 Java 后端面试的主战场**，且都能结合你的项目落地讲，不空洞。

---

## 4. 方向三：用户体系 + 安全（低成本高回报）

> 这块 `next-development-roadmap` 已有详细方案（真实用户体系、去掉 `DEFAULT_USER_ID`），这里只补「面试价值视角」的增量。

- **JWT + Spring Security**：roadmap 说「第一版可先用简单 token」，但**面向面试，建议直接上 JWT + Spring Security 过滤器链**。因为「Spring Security 认证授权流程」「JWT 无状态鉴权 vs Session」是高频考点，简单 token 讲不出深度。
- **接口幂等**：AI 任务提交、文章发布做幂等（Token 机制 / 唯一索引），结合 Redis 讲。
- **权限控制**：草稿/文章的归属校验（你已有 `validateOwner`，可讲「越权防护」——这是安全岗和后端岗都爱问的）。
- **敏感信息**：密码加盐哈希（BCrypt），别明文。

面试价值：Spring Security 过滤器链、JWT 原理与续签、CSRF/XSS 防护、越权（水平/垂直）防护。

---

## 5. 方向四：可观测性与工程化

> **目标**：从「能跑」到「线上可维护」，体现工程成熟度。这是区分「学生项目」和「工业级项目」的关键。

- **统一异常处理**：全局 `@RestControllerAdvice`（检查你现在是否已有，若无必须加）。
- **日志规范**：结构化日志 + traceId 全链路追踪（MDC）。一个请求从 Controller 到 Agent 调用能用一个 traceId 串起来，demo 时展示很惊艳。
- **链路追踪**：SkyWalking / Micrometer + Zipkin，追踪「一次 AI 写作请求各阶段耗时」。
- **监控**：Prometheus + Grafana，监控 QPS、RT、AI 调用成功率、token 消耗。
- **CI/CD**：GitHub Actions 跑测试 + 构建镜像（你已有测试用例基础，加个 CI 很快）。
- **测试**：你已经有不少测试类，可以讲「测试覆盖率」「单元测试 vs 集成测试分层」。

面试价值：这个方向能让你在「你怎么排查线上问题」「你的项目怎么保证质量」这类问题上，答得比 90% 的候选人具体。

---

## 6. 方向五：社区能力 + 复杂查询（可选）

> `next-development-roadmap` 第 7 节已有完整功能方案（标签筛选、作者主页、点赞、收藏、评论、推荐流），这里只标注**哪些有面试深度、哪些只是堆功能**。

| 功能 | 面试深度 | 建议 |
|------|---------|------|
| 点赞 / 收藏 | 高（缓存计数、防重、Redis+DB 一致性） | 值得做，能结合方向二 |
| 评论 | 中（树形结构、分页） | 一级评论即可 |
| 推荐 Feed | 高（多字段排序、热度算法、缓存） | 做简单版就好，别陷进推荐算法 |
| 标签筛选 / 作者主页 | 低（就是加查询条件） | 顺手做，别当亮点讲 |

**判断原则**：社区功能容易变成「堆 CRUD」，堆得再多面试也不加分。**只做「能带出中间件/性能优化话题」的那几个（点赞计数、热门 Feed 缓存），其余快速带过。**

---

## 7. 方向六：部署与云原生（加分项）

- **Docker 化**：你 docs 里已有 `docker-compose`，把前后端 + MySQL + Redis 一键拉起，README 里放启动命令，面试官/自己都能快速跑起来。
- **压测数据**：用 JMeter / wrk 压一下核心接口，**在简历里写出具体 QPS 数字**（哪怕是单机几百 QPS），比「高并发」三个字有说服力一百倍。
- **K8s**：加分但重，除非投运维/基础架构岗，否则「会 Docker + 讲清楚 K8s 概念」即可。

---

## 8. 给你的最终建议：秋招时间线与取舍

你 8 月开始面，现在（7 月）到 8 月大约 1 个月准备期。建议：

### 8.1 必做（撑起项目的双核，约 3 周）
1. **方向一（1）+（2）**：跑通多 Agent 编排 + 加 RAG。这是你区别于所有「AI 博客」项目的核心。
2. **方向二 3.1 + 3.3**：Redis 用出 3 个真实场景（缓存 / 分布式锁 / AI 限流）+ 数据库索引优化。
3. **方向三**：JWT + Spring Security 最小闭环（顺便完成 roadmap 的真实用户体系）。

### 8.2 有余力再做（锦上添花）
4. 方向一（3）工具调用可视化（demo 效果炸裂，性价比高，建议挤时间做）。
5. 方向四：traceId 全链路 + 一份压测数据。
6. 方向二 3.2：MQ 一个异步场景。

### 8.3 明确「不做」的（避免浪费时间）
- 不做复杂社区（评论楼中楼、复杂推荐算法）。
- 不做分库分表、K8s 生产级部署（除非专门投相关岗）。
- 不追求功能数量，追求「每个技术点能讲 5 分钟深度」。

### 8.4 一句话简历描述参考
> **AI Agent 技术写作平台**：基于 Spring Boot + DDD 分层架构，集成 Google ADK / Spring AI 构建多 Agent 写作流水线（规划-写作-配图-质检，支持串行/并行/循环编排），通过 MCP 协议接入 draw.io 画图与 PPT 生成工具；引入 RAG 检索增强降低幻觉；使用 Redis 实现缓存/分布式锁/AI 接口限流，单机压测核心接口 QPS 达 XXX；JWT + Spring Security 完成认证授权与越权防护。

---

## 9. 面试高频问题预演（照着这个补短板）

> 做完扩展后，确保这些问题你都能答出「结合项目的具体实现」，而不是背八股：

**Agent 方向**
1. 你说的 Agent 和 ChatGPT 直接问答有什么本质区别？
2. MCP 是什么？解决了什么问题？你怎么用的？
3. 多 Agent 怎么协作？你项目里哪一步用了编排？
4. RAG 完整流程？怎么评估效果？怎么解决检索不准？
5. 怎么防止大模型幻觉？怎么控制 token 成本？

**后端方向**
6. Redis 在你项目里解决了什么？缓存三大问题怎么处理？
7. 分布式锁怎么实现？Redisson 看门狗原理？
8. 为什么给 AI 接口做限流？令牌桶怎么实现？
9. 你的分页查询怎么优化的？深分页问题？
10. Spring Security 认证流程？JWT 怎么续签？
11. 怎么保证接口幂等？

**工程方向**
12. 线上出问题你怎么排查？（traceId / 日志 / 监控）
13. 你的项目并发能力如何？（压测数据）
14. DDD 分层的好处？为什么这么分模块？

---

*本方案与 `next-development-roadmap-2026-07-08.md`（产品视角）配合使用：产品 roadmap 决定做什么功能，本方案决定补什么技术深度、怎么在面试里讲。*

---

## 10. 按上述方案改造后，秋招是否有优势？（正面回答你的第二个问题）

**结论：有明显优势，但优势的大小取决于你投的岗位，以及你把哪几个方向做到了「能追问 5 分钟」的深度。** 分三种情况讲清楚，不给你灌鸡汤。

### 10.1 结论先行

| 目标岗位 | 改造后竞争力 | 说明 |
|----------|-------------|------|
| **大模型应用 / AI Agent 研发** | **强（第一梯队学生项目）** | 多 Agent 编排 + MCP + RAG 是这个岗位的核心考点，你有真实落地的编排链路，绝大多数候选人只有「调 API」。 |
| **Java 后端（含 AI 概念加分）** | **中上（明显高于平均）** | 补齐 Redis/MQ/安全/可观测后，八股有真实项目支撑；AI Agent 是差异化记忆点，面试官会主动追问，帮你占据谈话主动权。 |
| **纯 CRUD 后端 / 传统行业** | **中（够用但非碾压）** | 这类岗更看重中间件和并发深度，Agent 部分是加分点但不是决定因素，得靠方向二撑。 |

### 10.2 为什么说有优势——三个真实差异点

1. **稀缺性**：校招里「真正做过多 Agent 编排（不是调 API）」的学生**非常少**。你已有 `analyst→drawer→reviewer` 这种带角色分工、自我审查、联网检索的工作流，这是天然的「面试话题钩子」——面试官会顺着问下去，而你有东西可讲。
2. **深度可挖**：这个项目能同时覆盖 **AI 前沿（Agent/RAG/MCP）+ Java 工程（DDD/中间件/并发）+ 工程化（可观测/部署）** 三个维度。大多数学生项目只能覆盖其中一个。三线交叉是「有区分度」的关键。
3. **可演示**：draw.io / PPT 的流式生成 + 工具调用过程可视化，是**能当场 demo 的**。面试里「打开看效果」比「口头描述」的说服力高一个量级。

### 10.3 但要清醒——优势不是自动的，取决于三件事

1. **深度 > 数量**。6 个方向都做浅 = 没有优势。**方向一 + 方向二做到能被追问 5 分钟不露怯**，才是优势。宁可只讲透 3 个点，别铺 10 个点被一问就穿。
2. **必须能讲清「为什么」而非「用了什么」**。面试官不关心你用了 Redis，关心「你为什么在 AI 接口上做限流、令牌桶为什么用 Lua、雪崩怎么防」。**每个技术选型都要准备好「解决了什么问题 + 有什么替代方案 + 为什么选它」。**
3. **诚实边界**。像这次 draw.io/MCP 的更正一样——面试时把「已落地」和「设计但没跑」分清楚。说「我设计了但没完全跑通」不丢分，把没做的说成做了、被追问穿帮才是致命的。

### 10.4 决定竞争力上限的两个「关键动作」

如果时间有限只能抓两件事，抓这两个，性价比最高：

1. **把 draw.io 已验证的多 Agent 模式复制到「文章写作」主链路**（方向一(1)）。
   成本低（模式现成）、收益高（让核心业务而非边角功能体现 Agent 价值），且能讲「工作流可复用/可嵌套」。
2. **RAG 补齐 + AI 接口限流**（方向一(2) + 方向二 3.1）。
   RAG 是 AI 岗必问的最大空白；AI 限流是「有真实业务动机（LLM 调用贵）」的中间件场景，两个都极好讲。

做完这两个，加上你**已有的多 Agent 编排**，这个项目在 AI 相关岗位就已经是「能打」的水平了。

### 10.5 一句话总结

> **改造前**：一个「架构规范、接了 Agent 框架」的进阶学生项目，能过简历关，但面试易被问到底。
> **改造后（重点做方向一、二）**：一个「有真实多 Agent 编排 + RAG + 中间件深度」的差异化项目，在 AI/Agent 岗是第一梯队，在 Java 后端岗是明显加分项。
> **前提**：深度做透 3-4 个方向，每个技术选型都能讲清「为什么」，诚实区分「已落地」和「已设计」。


