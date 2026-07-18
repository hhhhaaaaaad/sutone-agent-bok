# 阶段二 & 阶段三 技术方案 + 项目上下文总结

> 用于新对话窗口的完整上下文交接。

---

# 第一部分：项目上下文

## 1. 项目概览

**项目名称**: sutone-agent-bok（AI Agent 技术写作平台）

**技术栈**:
- 后端: Spring Boot 3.4.3, Java 17, MyBatis, MySQL 8.0 (Docker :13306), Redis 6.2 (Docker :16379)
- 前端: Next.js, React, Milkdown (markdown 编辑器), KaTeX
- AI: Google ADK 0.5.0 + Spring AI 1.1.0-M3, DeepSeek API
- Agent 编排: 自研 Armory Assembly System (策略树模式)

**DDD 模块结构**:
```
sutone-agent-bok-app/          # Spring Boot 启动 + 配置
sutone-agent-bok-api/          # API 接口 + DTO
sutone-agent-bok-domain/       # 领域层 (account / content / agent)
sutone-agent-bok-infrastructure/ # 基础设施 (MyBatis DAO / Repository)
sutone-agent-bok-trigger/      # Controller + Security + filter
sutone-agent-bok-types/        # 枚举 / 异常
```

## 2. 已完成功能

### 阶段〇: 消费链路修复
- `AiWritingService.generateStream` 按 `event.author()` 区分三个子 Agent（analyst / generator / reviewer）
- 仅 reviewer 输出进入最终结果（落库），其余仅用于前端阶段展示
- 前端 `PhaseIndicator` 三阶段进度条（分析草稿 → 生成内容 → 质量审查）

### Markdown 格式治理 (计划外)
- `MarkdownNormalizer`: CommonMark AST 解析 + 重渲染（根治 A 类正则误切）
- `MarkdownBlockRenderer`: 结构化块（表格、代码块）确定性渲染
- 三层防线: YAML prompt → CommonMark AST → 前端 `normalizeMarkdown`

### 阶段一: 用户认证体系
- httpOnly Cookie + JWT (HS256) + BCrypt
- Spring Security 过滤器链（`JwtAuthenticationFilter` → Cookie 解析 → SecurityContext）
- `AuthUtil.getCurrentUserId()` 替换全部 3 个 Controller 的 `DEFAULT_USER_ID = 1L`
- Domain 层越权防护: `validateOwner(userId)`
- 前端: 登录页 + middleware.ts 路由守卫 + 注册功能

### 前端编辑器替换
- MDXEditor → Milkdown (Crepe)，支持 KaTeX 公式实时渲染
- 公式插入对话框（∑ 按钮 + 实时预览）
- 深色/浅色主题切换

### 分页 & 搜索
- 文章广场: 搜索（防抖 400ms）+ 真分页 + 每页条数选择 + 跳页
- 个人中心: 按用户过滤文章 + 分页
- 草稿页: 真分页（total 从 `countByUserId` 获取）

## 3. 关键代码路径

### Agent 写作流
```
AiWritingController.generateStream(taskId)
  → AiWritingService.generateStream()
    → chatService.handleMessageStream(agentId=300002, sessionId, prompt)
      → Flowable<Event>
        → analyst events (仅阶段展示)
        → generator events (前端预览，不落库)
        → reviewer events (逐行 consumeReviewerLine)
          → MarkdownBlockRenderer (结构化块)
          → responseBuilder 累积
    → formatMarkdown(responseBuilder)  // CommonMark AST
    → markSuccess(task, formattedContent)
    → resultEvent(formattedContent) → SSE 推前端
```

### Agent 装配 (Armory)
```
RootNode → AiApiNode → ChatModelNode → AgentNode
  → AgentWorkflowNode → SequentialAgentNode → RunnerNode
```

Agent YAML 加载:
```
application.yml spring.config.import:
  - classpath:agent/agent-writing.yml
  - classpath:agent/agent-draw-io.yml
  - classpath:agent/agent-ppt.yml
```

### 当前写作 Agent 配置 (`agent-writing.yml`)
```
agents:
  - agent_writing_analyst    output-key: writing_analysis
  - agent_writing_generator  output-key: draft_content
  - agent_writing_reviewer   output-key: final_result

workflow: sequential [analyst → generator → reviewer]
runner: agent-name: sequential_writing_process
```

### 当前 Draw.io Agent 配置 (`agent-draw-io.yml`)
```
agents:
  - agent_analyst    output-key: analysis_result
  - agent_drawer     output-key: draft_diagram
  - agent_reviewer   output-key: final_result

workflows:
  - loop_refinement       (loop, 3次): drawer → reviewer
  - parallel_generation   (parallel):   drawer, drawer
  - sequential_draw_process (sequential): analyst → drawer  ← 入口

runner: agent-name: sequential_draw_process
```

---

# 第二部分：阶段二 — 多 Agent 写作流水线 + draw.io 配图嵌入

## 1. 目标

将当前的「analyst → generator → reviewer」三阶段固定流水线升级为「analyst → generator → illustrator → reviewer」四阶段流水线，配图环节复用已有的 draw.io 三 Agent 子工作流。

## 2. 核心思路：应用层编排（方式 A）

### 为什么不直接用 ADK 子 Agent 嵌套

Google ADK 的 `SequentialAgent` 接受 `List<BaseAgent>` 作为 sub-agents。理论上可以直接将 `sequential_draw_process` 作为一个子节点嵌入 `sequential_writing_pipeline`。但 ADK 0.5.0 版本的工作流嵌套支持需验证——不确定 `LoopAgent`/`ParallelAgent` 的结果能否作为另一个 workflow 的子节点。

**方式 A（推荐）**: 在 `AiWritingService.generateStream` 中做应用层编排——写作流水线遇到配图需求时，用 draw.io 的 agentId (300000) 发起子会话，拿到 drawio XML 后插回文章。

优点:
- 两条 Agent 链完全解耦，draw.io 链零改动
- 能讲「工作流可组合/可复用」——同一套 draw.io 子流程既服务画图页，也服务写作配图
- 实现确定性高，不依赖 ADK 版本特性

## 3. 具体实现

### 3.1 新增 illustrator Agent

在 `agent-writing.yml` 的 `agents` 列表中新增:

```yaml
- name: agent_writing_illustrator
  description: 识别正文中适合配图的段落，产出绘图需求描述
  instruction: |
    分析 {draft_content}，找出适合用架构图/流程图/时序图表达的段落。
    对每个需要配图的点，输出一行 JSON：
    {"type":"illustration_request","anchor":"段落首句","diagramType":"architecture|flowchart|sequence","requirement":"画什么"}
    若无需要配图的段落，输出 {"type":"illustration_request","none":true}
    不要输出任何解释说明。
  output-key: illustration_requests
```

### 3.2 更新 Workflow

```yaml
agent-workflows:
  - type: sequential
    name: sequential_writing_pipeline
    sub-agents:
      - agent_writing_analyst
      - agent_writing_generator
      - agent_writing_illustrator    # ← 新增
      - agent_writing_reviewer

runner:
  agent-name: sequential_writing_pipeline
```

### 3.3 AiWritingService 消费 illustrator 输出

在 `generateStream` 的 `blockingForEach` 中新增 illustrator 处理分支:

```java
// 消费 illustrator 的配图需求
if (AUTHOR_ILLUSTRATOR.equals(author)) {
    consumeIllustratorLine(line, responseBuilder, eventConsumer);
    return;
}
```

`consumeIllustratorLine` 逻辑:
1. 解析 `{"type":"illustration_request",...}` JSON
2. 若 `none: true` → 跳过
3. 否则 → 用 draw.io 的 agentId (300000) 发起子会话:
   ```java
   String drawSessionId = chatService.createSession("300000", String.valueOf(userId));
   String drawPrompt = buildDrawPrompt(illustrationRequest);
   Flowable<Event> drawEvents = chatService.handleMessageStream(
       "300000", userId, drawSessionId, drawPrompt);
   // 消费 drawEvents，提取 drawio_done 事件的 XML
   ```
4. 将拿到的 drawio XML 注入 `responseBuilder` 对应锚点位置

### 3.4 更新前端 PhaseIndicator

```typescript
const PHASE_LABELS: Record<string, string> = {
  analyzing: "分析草稿",
  generating: "生成内容",
  illustrating: "识别配图",    // ← 新增
  reviewing: "质量审查",
  thinking: "思考中",
};

const AUTHOR_PHASE_MAP = Map.of(
  "agent_writing_analyst", "analyzing",
  "agent_writing_generator", "generating",
  "agent_writing_illustrator", "illustrating",  // ← 新增
  "agent_writing_reviewer", "reviewing"
);
```

### 3.5 改动清单

| 文件 | 改动 |
|------|------|
| `agent-writing.yml` | 新增 `agent_writing_illustrator` + 更新 workflow |
| `AiWritingService.java` | 新增 `consumeIllustratorLine` + 子会话编排 |
| `AiWritingPanel/index.tsx` | PhaseLabel 新增 "识别配图" |

## 4. 面试话术

> 「写作是一条四阶段 Agent 流水线：规划、写作、配图、质检。配图环节我没有重写画图能力，而是把已有的 draw.io 三 Agent 子工作流（分析→绘图→审查）作为子流程复用——写作流水线识别出配图需求后，用绘图子工作流生成结构化 XML 再插回文章。这体现了工作流的可组合和可复用，一套 draw.io 编排同时服务画图页和文章配图两个场景。」

---

# 第三部分：阶段三 — RAG 检索增强

## 1. 目标

让写作 Agent 在生成前先检索用户历史文章 / 技术知识库，降低幻觉、提升与用户既有内容的一致性。

## 2. 技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| 向量库 | **MySQL + 向量存储（内存/文件）或 PostgreSQL + pgvector** | Spring AI 原生支持 `PgVectorStore`；项目已有 MySQL 容器，可加 pgvector 容器 |
| Embedding | 复用 `agent-writing.yml` 的 `embeddings-path`（DeepSeek 兼容 OpenAI 格式） | 零额外接入成本 |
| 框架 | Spring AI `VectorStore` + `EmbeddingModel` | 与现有 Spring AI 一致 |

**初期简化方案**: 不需要立马上 pgvector。可以先用内存向量存储（`SimpleVectorStore`）验证链路，再换 pgvector。数据量小时（< 1000 篇文章），内存方案完全够用。

## 3. 架构设计

### 3.1 建库流程

```
用户历史文章
  → DocumentChunker (按 ## 标题切分，overlap=20%)
  → EmbeddingClient (调 embeddings-path)
  → VectorStore (存入 pgvector / 内存)
```

### 3.2 检索流程（两种接入方式）

**方式 A（推荐先做）: 前置检索**

在 `AiWritingService.submitTask` 中，`buildPrompt` 之前先检索:

```java
// RagRetrievalService.retrieve(draft内容, topK=3)
List<Document> relevantDocs = ragRetrievalService.retrieve(
    draft.getContentMd(), 3);

// 拼进 prompt
String ragContext = relevantDocs.stream()
    .map(Document::getContent)
    .collect(Collectors.joining("\n\n"));
String prompt = buildPromptWithContext(draft, taskType, ragContext);
```

**方式 B（进阶）: Agentic RAG**

把检索封装成 MCP Tool，挂到 `agent-writing.yml` 的 `tool-mcp-list`:

```yaml
tool-mcp-list:
  - local:
      name: rag-search
      description: 检索用户历史文章和知识库
```

Agent 自己决定「要不要查、查什么」→ 更 Agent 原生 → 面试能对比两种范式。

### 3.3 完整链路

```
建库:
  文章 → chunker → embedding → vector store

检索:
  query → embedding → vector store topK → 重排 → 拼 prompt → Agent 生成
```

## 4. 新增文件

```
domain/agent/service/rag/
├── RagRetrievalService.java       # 检索服务
├── EmbeddingClient.java           # 调用 embeddings-path
└── DocumentChunker.java           # 文章切分

infrastructure/rag/
└── VectorStoreClient.java         # 封装 pgvector 读写
```

## 5. 面试话术

> 「为降低幻觉，我加了 RAG：把用户历史文章按标题切分、embedding 后存进 pgvector，写作前检索 topK 相关片段拼进 prompt。我实现了两种接入——固定前置检索，以及把检索封装成 MCP 工具让 Agent 自主决定是否调用（Agentic RAG），后者复用了我已有的本地 MCP 工具机制。」

---

# 第四部分：实施优先级

| 顺序 | 内容 | 预估工期 | 依赖 |
|------|------|---------|------|
| 1 | 阶段二: illustrator Agent + YAML 更新 | 2-3 天 | 无 |
| 2 | 阶段二: `consumeIllustratorLine` + 子会话编排 | 2-3 天 | 步骤 1 |
| 3 | 阶段二: 前端 PhaseLabel 更新 | 0.5 天 | 步骤 2 |
| 4 | 阶段三: DocumentChunker + EmbeddingClient | 1-2 天 | 无 |
| 5 | 阶段三: VectorStoreClient + MySQL/pgvector | 1-2 天 | 步骤 4 |
| 6 | 阶段三: RagRetrievalService + 前置检索 | 1-2 天 | 步骤 5 |
| 7 | 阶段三(进阶): Agentic RAG (MCP Tool) | 1-2 天 | 步骤 6 |

阶段二总计: ~5-6 天，阶段三总计: ~5-7 天。

---

# 第五部分：关键文件速查

| 文件 | 用途 |
|------|------|
| `agent-writing.yml` | 写作 Agent 配置 (新增 illustrator + 更新 workflow) |
| `agent-draw-io.yml` | draw.io Agent 配置 (已有，不动) |
| `AiWritingService.java` | 写作流消费者 (加 illustrator 分支 + 子会话编排) |
| `AiAgentAutoConfig.java` | Agent 启动装配入口 |
| `SequentialAgentNode.java` | SequentialAgent 装配 |
| `ChatModelNode.java` | MCP/Skills 工具回调注入点 (RAG 接入位) |
| `AiApiNode.java` | OpenAiApi 构建 (embeddingsPath 注入位) |
| `JwtAuthenticationFilter.java` | Cookie → JWT → SecurityContext |
| `SecurityConfig.java` | Spring Security 过滤器链配置 |
| `MarkdownNormalizer.java` | CommonMark AST 规范化 |
| `AiWritingPanel/index.tsx` | 前端 AI 写作面板 + PhaseIndicator |

---

*本文档用于新对话窗口的上下文交接。实施时回查 `final_plan.md` 第 5、6 节获得更完整的设计细节。*
