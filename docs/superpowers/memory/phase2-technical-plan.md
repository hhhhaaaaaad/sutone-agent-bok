# 阶段二技术方案：多 Agent 写作流水线 + draw.io 配图嵌入（可选开关）

> 基于 `phase2-phase3-design-and-context.md` 第二部分和 `final_plan.md` 第 5 节的细化施工方案。
> 编写日期：2026-07-12，更新：加入 enableIllustration 可选开关。

---

## 1. 目标

将当前三 Agent 固定流水线升级为可选配图的四阶段流水线。用户在提交写作任务时可选择「启用配图」，开启后 illustrator Agent 识别配图需求并复用 draw.io 子工作流生成配图 XML 嵌入文章；未开启则走原有三 Agent 链路，无额外开销。

---

## 2. enableIllustration 开关设计

### 2.1 参数传递链路

```
前端 toggle (enableIllustration: true/false)
  → POST /ai-writing/task/submit  { enableIllustration: true }
    → AiWritingController.submitTask()
      → AiWritingService.submitTask(userId, draftId, taskType, promptParams, enableIllustration)
        → AiTaskEntity.initPending(..., enableIllustration)  ← 持久化到 DB
          → 返回 taskId

前端 SSE GET /ai-writing/task/stream?taskId=xxx
  → AiWritingController.stream(taskId)
    → AiWritingService.generateStream(taskId, userId, eventConsumer)
      → 从 DB 读取 task.enableIllustration
      → 决定是否处理 illustrator 输出 + 发起 draw.io 子会话
```

**关键点**：`submitTask` 和 `generateStream` 是两次独立 HTTP 请求，`enableIllustration` 必须持久化到 `ai_task` 表。

### 2.2 两种模式行为对比

| | enableIllustration = false（默认） | enableIllustration = true |
|---|---|---|
| YAML 配置 | 同一份（含 illustrator） | 同一份（含 illustrator） |
| 工作流 agent 数量 | 4（analyst→generator→**illustrator**→reviewer） | 4（同上） |
| illustrator 是否运行 | 运行（ADK 顺序工作流无法跳过） | 运行 |
| illustrator 输出处理 | **忽略**（不做 JSON 解析，不发起子会话） | 解析 illustration_request，发起 draw.io 子会话 |
| PhaseIndicator | 3 段（analyzing/generating/reviewing） | 4 段（analyzing/generating/illustrating/reviewing） |
| draw.io 子会话 | **0 次** | 按需 1-3 次 |
| LLM 调用增量 | 仅 illustrator 一次轻量文本分析 | illustrator + draw.io(analyst+drawer) × N |

---

## 3. 架构总览

```
                    写作流水线 (agentId=300002)
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ analyst  │ →  │generator │ →  │illustrator│ → │ reviewer │
│ 分析草稿  │    │ 写作正文  │    │ 识别配图  │    │ 质量审查  │
└──────────┘    └──────────┘    └─────┬────┘    └──────────┘
                                      │
                         产出 illustration_requests
                                      │
                    ┌─────────────────┴──────────────┐
                    │  enableIllustration?            │
                    │                                 │
                    │  false → 忽略 illustrator 输出   │
                    │  true  → 应用层编排 draw.io     │
                    └─────────────────┬──────────────┘
                                      │ (仅 enableIllustration=true)
                                      ▼
                            ┌──────────────────────┐
                            │  draw.io 子工作流     │  agentId=300000
                            │  analyst → drawer    │  (复用已有，零改动)
                            └──────────┬───────────┘
                                       │ 产出 drawio_done XML
                                       ▼
                                 注入 responseBuilder
                                       │
                                       ▼
                                 reviewer 审查
                                       │
                                       ▼
                                 formatMarkdown → 落库
```

---

## 4. 改动清单

### 4.1 数据库：ai_task 表加列

```sql
ALTER TABLE ai_task ADD COLUMN enable_illustration TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '是否启用配图 0-否 1-是'
  AFTER prompt_payload;
```

### 4.2 AiTaskPO — 新增字段

**文件**：`sutone-agent-bok-infrastructure/.../dao/po/AiTaskPO.java`

新增：
```java
private Integer enableIllustration;  // 0=否, 1=是
```

### 4.3 IAiTaskDao — 更新 SQL

**文件**：`sutone-agent-bok-infrastructure/.../dao/IAiTaskDao.java`

INSERT 新增字段：
```sql
INSERT INTO ai_task(id, user_id, draft_id, task_type, prompt_payload, enable_illustration,
  response_content, status, error_msg, is_deleted)
VALUES(#{id}, #{userId}, #{draftId}, #{taskType}, #{promptPayload}, #{enableIllustration},
  #{responseContent}, #{status}, #{errorMsg}, #{isDeleted})
```

SELECT queryById 新增字段：
```sql
SELECT id, user_id, draft_id, task_type, prompt_payload, enable_illustration,
  response_content, status, error_msg, create_time, update_time, is_deleted
FROM ai_task WHERE id = #{taskId} AND is_deleted = 0
```

queryByDraftId 同样新增 `enable_illustration` 字段。

### 4.4 AiTaskEntity — 新增字段

**文件**：`sutone-agent-bok-domain/.../model/entity/AiTaskEntity.java`

```java
private Boolean enableIllustration;  // 是否启用配图
```

`initPending` 方法新增参数：
```java
public static AiTaskEntity initPending(Long taskId, Long userId, Long draftId,
        AiWritingTaskTypeVO taskType, String promptPayload, Boolean enableIllustration) {
    // ... 原有校验 ...
    return AiTaskEntity.builder()
            // ... 原有字段 ...
            .enableIllustration(null != enableIllustration && enableIllustration)
            .build();
}
```

### 4.5 AiTaskRepository — 更新 PO ↔ Entity 转换

**文件**：`sutone-agent-bok-infrastructure/.../adapter/repository/AiTaskRepository.java`

`toPO` 新增：
```java
.enableIllustration(Boolean.TRUE.equals(entity.getEnableIllustration()) ? 1 : 0)
```

`toEntity` 新增：
```java
.enableIllustration(null != po.getEnableIllustration() && po.getEnableIllustration() == 1)
```

### 4.6 SubmitAiTaskRequestDTO — 新增字段

**文件**：`sutone-agent-bok-api/.../dto/aiwriting/SubmitAiTaskRequestDTO.java`

```java
private Boolean enableIllustration;  // 默认 false（前端不传 = 不启用）
```

### 4.7 IAiWritingService（domain）— 方法签名更新

**文件**：`sutone-agent-bok-domain/.../service/IAiWritingService.java`

`submitTask` 新增参数：
```java
AiTaskEntity submitTask(Long userId, Long draftId, String taskTypeCode,
    Map<String, Object> promptParams, Boolean enableIllustration);
```

### 4.8 AiWritingService.submitTask — 透传 enableIllustration

**文件**：`sutone-agent-bok-domain/.../service/ai_writing/AiWritingService.java`

```java
@Override
public AiTaskEntity submitTask(Long userId, Long draftId, String taskTypeCode,
        Map<String, Object> promptParams, Boolean enableIllustration) {
    // ... 原有逻辑 ...
    AiTaskEntity task = AiTaskEntity.initPending(taskId, userId, draftId, taskType,
            prompt, enableIllustration);
    // ...
}
```

### 4.9 AiWritingController.submitTask — 读取 DTO 字段

**文件**：`sutone-agent-bok-trigger/.../http/AiWritingController.java`

```java
AiTaskEntity task = aiWritingService.submitTask(
        AuthUtil.getCurrentUserId(),
        requestDTO.getDraftId(),
        requestDTO.getTaskType(),
        requestDTO.getPromptParams(),
        requestDTO.getEnableIllustration()  // 新增
);
```

### 4.10 agent-writing.yml — 新增 illustrator Agent

**文件**：`sutone-agent-bok-app/src/main/resources/agent/agent-writing.yml`

在 agents 列表中，generator 和 reviewer 之间新增：

```yaml
- name: agent_writing_illustrator
  description: 识别正文中适合配图的段落，产出绘图需求描述
  instruction: |
    你是一个技术文章配图分析员。请分析输入 {draft_content}，找出适合用架构图/流程图/时序图表达的段落。

    【识别规则】
    1. 涉及多组件协作、系统架构、数据流转的段落 → architecture（架构图）
    2. 涉及业务流程、审批流、决策分支的段落 → flowchart（流程图）
    3. 涉及请求/响应时序、消息传递的段落 → sequence（时序图）
    4. 每个段落最多配一张图，只选最有价值的 1-3 处

    【输出格式】
    对每个需要配图的点，输出一行 JSON：
    {"type":"illustration_request","anchor":"段落首句或标题","diagramType":"architecture|flowchart|sequence","requirement":"具体画什么"}
    若无需要配图的段落，输出一行：
    {"type":"illustration_request","none":true}
    不要输出任何解释说明，每行一个完整 JSON。
  output-key: illustration_requests
```

更新 workflow：
```yaml
agent-workflows:
  - type: sequential
    name: sequential_writing_process
    description: 标准技术写作流程：分析草稿 → 生成内容 → 识别配图 → 质量审查
    sub-agents:
      - agent_writing_analyst
      - agent_writing_generator
      - agent_writing_illustrator
      - agent_writing_reviewer
```

### 4.11 AiWritingService.generateStream — 核心条件编排

**文件**：`sutone-agent-bok-domain/.../service/ai_writing/AiWritingService.java`

**新增常量**：
```java
private static final String AUTHOR_ILLUSTRATOR = "agent_writing_illustrator";
private static final String DRAWIO_AGENT_ID = "300000";

// AUTHOR_PHASE_MAP 始终包含 illustrator（phase 映射不区分开关，前端根据实际事件渲染）
private static final Map<String, String> AUTHOR_PHASE_MAP = Map.of(
        AUTHOR_ANALYST, "analyzing",
        AUTHOR_GENERATOR, "generating",
        AUTHOR_ILLUSTRATOR, "illustrating",
        AUTHOR_REVIEWER, "reviewing"
);

private static final Map<String, String> PHASE_LABEL_MAP = Map.of(
        "analyzing", "正在分析草稿上下文...",
        "generating", "正在生成写作内容...",
        "illustrating", "正在识别配图需求...",
        "reviewing", "正在进行质量审查..."
);
```

**generateStream 核心变更**（在 `blockingForEach` 内）：

```java
@Override
public void generateStream(Long taskId, Long userId, Consumer<AiWritingStreamEventVO> eventConsumer) {
    AiTaskEntity task = queryTask(taskId, userId);
    task.startRunning();
    aiTaskRepository.update(task);

    String agentId = resolveAgentId();
    String sessionId = chatService.createSession(agentId, String.valueOf(userId));
    StringBuilder responseBuilder = new StringBuilder();
    StringBuilder reviewerLineBuffer = new StringBuilder();

    // 配图相关
    boolean enableIllustration = Boolean.TRUE.equals(task.getEnableIllustration());
    List<IllustrationRequest> illustrationRequests = new ArrayList<>();

    try {
        Flowable<Event> events = chatService.handleMessageStream(
                agentId, String.valueOf(userId), sessionId, task.getPromptPayload());
        String[] currentPhase = {null};
        events.blockingForEach(event -> {
            if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) {
                return;
            }
            String author = event.author();
            String newPhase = AUTHOR_PHASE_MAP.getOrDefault(author, "thinking");
            if (!Objects.equals(newPhase, currentPhase[0])) {
                currentPhase[0] = newPhase;
                eventConsumer.accept(statusEvent(newPhase,
                        PHASE_LABEL_MAP.getOrDefault(newPhase, "思考中...")));
            }
            String content = event.stringifyContent();
            if (null == content || content.isBlank()) {
                return;
            }

            // === analyst：仅阶段展示 ===
            if (AUTHOR_ANALYST.equals(author)) {
                return;
            }

            // === generator：token 预览，不落库 ===
            if (AUTHOR_GENERATOR.equals(author)) {
                eventConsumer.accept(tokenEvent(newPhase, content));
                return;
            }

            // === illustrator（新增）：仅 enableIllustration=true 时解析 ===
            if (AUTHOR_ILLUSTRATOR.equals(author)) {
                if (enableIllustration) {
                    for (String line : content.split("\n")) {
                        consumeIllustratorLine(line, illustrationRequests);
                    }
                }
                // enableIllustration=false 时直接忽略 illustrator 输出
                return;
            }

            // === reviewer：终稿累积（同现有逻辑） ===
            boolean isPartial = event.partial().orElse(false);
            reviewerLineBuffer.append(content);
            // ... 后续同现有行装配逻辑 ...
        });

        // flush reviewer 残留缓冲
        // ...

        // ===== 配图子会话编排（仅 enableIllustration=true） =====
        if (enableIllustration && !illustrationRequests.isEmpty()) {
            eventConsumer.accept(statusEvent("illustrating", "正在生成配图..."));
            for (IllustrationRequest req : illustrationRequests) {
                try {
                    String drawXml = generateIllustration(userId, req);
                    if (null != drawXml && !drawXml.isBlank()) {
                        injectIllustration(responseBuilder, req.anchor(), drawXml, eventConsumer);
                    }
                } catch (Exception e) {
                    log.error("生成配图失败 anchor={}: {}", req.anchor(), e.getMessage());
                }
            }
        }

        String formattedContent = formatMarkdown(responseBuilder.toString());
        markSuccess(task, formattedContent);
        eventConsumer.accept(resultEvent(formattedContent));
        eventConsumer.accept(doneEvent());
    } catch (Exception e) {
        // ... 同现有异常处理 ...
    }
}
```

**新增内部类和方法**：

```java
private record IllustrationRequest(String anchor, String diagramType, String requirement) {}

private void consumeIllustratorLine(String line, List<IllustrationRequest> requests) {
    if (null == line || line.isBlank()) return;
    try {
        JSONObject json = JSON.parseObject(line.trim());
        if (json.containsKey("none") && json.getBoolean("none")) return;
        String anchor = json.getString("anchor");
        String diagramType = json.getString("diagramType");
        String requirement = json.getString("requirement");
        if (null != anchor && null != diagramType && null != requirement) {
            requests.add(new IllustrationRequest(anchor, diagramType, requirement));
        }
    } catch (Exception e) {
        log.warn("解析 illustrator 输出失败，跳过该行: {}", line, e);
    }
}

private String generateIllustration(Long userId, IllustrationRequest req) {
    String drawSessionId = chatService.createSession(DRAWIO_AGENT_ID, String.valueOf(userId));
    String drawPrompt = """
            请根据以下绘图需求，生成一个 draw.io 图表。
            图表类型：%s
            需求描述：%s
            """.formatted(req.diagramType(), req.requirement());

    Flowable<Event> drawEvents = chatService.handleMessageStream(
            DRAWIO_AGENT_ID, String.valueOf(userId), drawSessionId, drawPrompt);

    String[] drawXml = {null};
    drawEvents.blockingForEach(event -> {
        if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) return;
        String content = event.stringifyContent();
        if (null == content || content.isBlank()) return;
        for (String line : content.split("\n")) {
            try {
                JSONObject json = JSON.parseObject(line.trim());
                if ("drawio_done".equals(json.getString("type"))) {
                    drawXml[0] = json.getString("content");
                }
            } catch (Exception ignored) {}
        }
    });
    return drawXml[0];
}

private void injectIllustration(StringBuilder responseBuilder, String anchor,
        String drawXml, Consumer<AiWritingStreamEventVO> eventConsumer) {
    String diagramBlock = "\n```drawio\n" + drawXml + "\n```\n";
    int anchorPos = responseBuilder.indexOf(anchor);
    if (anchorPos >= 0) {
        int insertPos = anchorPos + anchor.length();
        int lineEnd = responseBuilder.indexOf("\n", insertPos);
        if (lineEnd >= 0) {
            responseBuilder.insert(lineEnd, "\n" + diagramBlock);
        } else {
            responseBuilder.insert(insertPos, "\n" + diagramBlock);
        }
    } else {
        responseBuilder.append("\n").append(diagramBlock);
    }
    eventConsumer.accept(tokenEvent("illustrating", diagramBlock));
}
```

### 4.12 前端改动

#### 4.12.1 提交 UI — toggle 开关

**文件**：`AiWritingPanel/index.tsx`

```typescript
// 新增状态
const [enableIllustration, setEnableIllustration] = useState(false);

// 提交时传入
await submitTask({
  draftId,
  taskType,
  promptParams,
  enableIllustration,  // 新增
});
```

开关 UI，在写作类型选择区域下方：

```tsx
<label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
  <input
    type="checkbox"
    checked={enableIllustration}
    onChange={(e) => setEnableIllustration(e.target.checked)}
  />
  启用 AI 配图（自动识别正文中的配图需求并生成架构图/流程图/时序图）
</label>
```

**PhaseIndicator 新增 illustrating 阶段**：

```typescript
const PHASE_LABELS: Record<string, string> = {
  analyzing: "分析草稿",
  generating: "生成内容",
  illustrating: "识别配图",
  reviewing: "质量审查",
  thinking: "思考中",
};
```

#### 4.12.2 draw.io 图表渲染 — DrawioViewer 组件

**文件**：`components/DrawioViewer/index.tsx`（**新建**）

渲染策略：利用 draw.io 官方的 `viewer-static.min.js` 做客户端渲染，与 Milkdown 已有 LaTeX 渲染模式一致。` ```drawio ` 代码块被检测到后，动态加载 viewer JS 并在容器 DOM 上创建交互式图表。

```tsx
'use client';

import { useEffect, useRef, useState } from 'react';

interface DrawioViewerProps {
  xml: string;
}

export default function DrawioViewer({ xml }: DrawioViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    container.innerHTML = '';

    const graphDiv = document.createElement('div');
    graphDiv.className = 'mxgraph';
    graphDiv.setAttribute('data-mxgraph', JSON.stringify({
      highlight: '#0000ff',
      nav: true,
      resize: true,
      toolbar: 'zoom layers lightbox',
      edit: '_blank',
      xml: xml,
    }));
    container.appendChild(graphDiv);

    // 避免重复加载 viewer 脚本
    const existing = document.querySelector(
      'script[src="https://viewer.diagrams.net/js/viewer-static.min.js"]'
    );
    if (existing) {
      (window as any).GraphViewer?.createViewer(graphDiv);
      return;
    }

    const script = document.createElement('script');
    script.src = 'https://viewer.diagrams.net/js/viewer-static.min.js';
    script.onload = () => {
      (window as any).GraphViewer?.createViewer(graphDiv);
    };
    script.onerror = () => setError(true);
    document.head.appendChild(script);

    return () => {
      // 不清理全局脚本，只清理容器（由 React 管理）
    };
  }, [xml]);

  if (error) {
    return (
      <details className="my-4 border rounded p-3 bg-gray-50">
        <summary className="cursor-pointer text-sm text-gray-500">
          图表 (draw.io) — 点击展开源码
        </summary>
        <pre className="mt-2 text-xs overflow-auto max-h-96">{xml}</pre>
      </details>
    );
  }

  return (
    <div
      ref={containerRef}
      className="drawio-viewer my-4 border rounded p-2 bg-white flex justify-center overflow-auto"
    />
  );
}
```

**容错说明**：viewer JS 加载失败时降级为折叠面板展示 XML 源码。

#### 4.12.3 编辑器渲染 — Milkdown Crepe 注入

**文件**：`components/MilkdownEditor/MilkdownEditorInner.tsx`

在 Crepe 实例化时注入 `featureConfigs`，复用已有的 `renderPreview` 模式（与 LaTeX 完全一致）：

```typescript
const crepe = new Crepe({
  root,
  defaultValue: markdown,
  features: {
    [CrepeFeature.Latex]: true,
    [CrepeFeature.TopBar]: true,
  },
  featureConfigs: {
    [CrepeFeature.CodeMirror]: {
      renderPreview: (language: string, content: string) => {
        // 保留已有 LaTeX 逻辑（由 Crepe 内置，此处仅示意）
        if (language === 'latex' && content.length > 0) {
          return null; // Crepe 内置 LaTeX 已处理
        }
        // drawio 预览
        if (language === 'drawio' && content.length > 0) {
          const container = document.createElement('div');
          container.className = 'drawio-preview';
          container.style.minHeight = '200px';
          // 异步渲染：先返回容器，再用 DrawioViewer 的逻辑填充
          renderDrawioInContainer(container, content);
          return container;
        }
        return null; // 回退到默认代码块展示
      },
    },
  },
});
```

`renderDrawioInContainer` 辅助函数（逻辑与 `DrawioViewer` 一致，但直接操作传入的 DOM 节点）：

```typescript
function renderDrawioInContainer(container: HTMLElement, xml: string): void {
  const graphDiv = document.createElement('div');
  graphDiv.className = 'mxgraph';
  graphDiv.setAttribute('data-mxgraph', JSON.stringify({
    highlight: '#0000ff',
    nav: true,
    resize: true,
    toolbar: 'zoom layers lightbox',
    edit: '_blank',
    xml: xml,
  }));
  container.appendChild(graphDiv);

  const existing = document.querySelector(
    'script[src="https://viewer.diagrams.net/js/viewer-static.min.js"]'
  );
  if (existing) {
    (window as any).GraphViewer?.createViewer(graphDiv);
    return;
  }
  const script = document.createElement('script');
  script.src = 'https://viewer.diagrams.net/js/viewer-static.min.js';
  script.onload = () => {
    (window as any).GraphViewer?.createViewer(graphDiv);
  };
  document.head.appendChild(script);
}
```

#### 4.12.4 文章阅读页渲染 — react-markdown 自定义 code 组件

**文件**：`components/MarkdownRenderer/index.tsx`

在 `ReactMarkdown` 的 `components` 中自定义 code 块渲染，检测 `language-drawio` 委托给 `DrawioViewer`：

```tsx
import DrawioViewer from '@/components/DrawioViewer';

<ReactMarkdown
  remarkPlugins={[remarkGfm, remarkMath]}
  rehypePlugins={[rehypeKatex, [rehypeHighlight, { detect: true, ignoreMissing: true }]]}
  components={{
    code({ node, className, children, ...props }) {
      const match = /language-(\w+)/.exec(className || '');
      const language = match ? match[1] : '';
      const content = String(children).replace(/\n$/, '');

      if (language === 'drawio' && content.length > 0) {
        return <DrawioViewer xml={content} />;
      }

      // 其他代码块走默认高亮
      return (
        <code className={className} {...props}>
          {children}
        </code>
      );
    },
  }}
>
  {renderContent}
</ReactMarkdown>
```

#### 4.12.5 前端渲染链路总结

```
markdown 中的 ```drawio <xml> ```
        │
        ├─ 编辑模式 → Milkdown Crepe
        │     └─ codeBlockConfig.renderPreview
        │         → language === 'drawio'
        │         → renderDrawioInContainer(container, xml)
        │         → draw.io viewer-static.min.js 渲染
        │
        └─ 阅读模式 → react-markdown (MarkdownRenderer)
              └─ components.code
                  → language === 'drawio'
                  → <DrawioViewer xml={content} />
                  → useEffect 加载 viewer JS + 创建 mxgraph div
```

### 4.13 改动文件汇总

| 文件 | 改动 |
|------|------|
| `ai_task` 表 | DDL: `ALTER TABLE ADD COLUMN enable_illustration TINYINT(1) DEFAULT 0` |
| `AiTaskPO.java` | 新增 `enableIllustration` 字段 |
| `IAiTaskDao.java` | INSERT/SELECT 新增字段 |
| `AiTaskEntity.java` | 新增 `enableIllustration` 字段 + `initPending` 加参 |
| `AiTaskRepository.java` | toPO/toEntity 新增字段映射 |
| `SubmitAiTaskRequestDTO.java` | 新增 `enableIllustration` 字段 |
| `IAiWritingService.java` (domain) | `submitTask` 加参 |
| `AiWritingService.java` | 常量 + illustrator 分支 + `consumeIllustratorLine`/`generateIllustration`/`injectIllustration` + 条件编排 |
| `AiWritingController.java` | `submitTask` 调用透传 `enableIllustration` |
| `agent-writing.yml` | 新增 `agent_writing_illustrator` + 更新 workflow |
| `AiWritingPanel/index.tsx` | toggle UI + PhaseLabel 更新 |
| `components/DrawioViewer/index.tsx` | **新建**：draw.io 客户端渲染组件 |
| `components/MilkdownEditor/MilkdownEditorInner.tsx` | Crepe `featureConfigs` 注入 `drawio` 预览 |
| `components/MarkdownRenderer/index.tsx` | 自定义 `code` 组件，委托给 `DrawioViewer` |

**不需要改动的文件**：
- `agent-draw-io.yml` — 零改动
- `ChatService.java` — 已支持任意 agentId
- `AiWritingStreamEventVO.java` — phase 为 String，无需新枚举
- `IAiWritingService.generateStream` — 无需改签名（enableIllustration 从 DB 读取）

---

## 5. 实施步骤（按依赖顺序）

### 步骤 1：DB 迁移
- 执行 `ALTER TABLE ai_task ADD COLUMN enable_illustration TINYINT(1) NOT NULL DEFAULT 0`
- 验证存量数据默认值为 0

### 步骤 2：数据层改造（PO → DAO → Repository → Entity）
- 按 4.2~4.5 逐层新增字段和映射

### 步骤 3：API 层改造（DTO → Controller）
- `SubmitAiTaskRequestDTO` 加字段
- `AiWritingController.submitTask` 透传

### 步骤 4：Domain 层核心编排
- `IAiWritingService.submitTask` 签名加参
- `AiWritingService` 实现：常量、illustrator 消费分支、子会话编排、条件开关

### 步骤 5：YAML 配置
- 新增 illustrator agent + 更新 workflow

### 步骤 6：前端 — 提交 UI
- PhaseIndicator 扩展（4 段）
- Toggle 开关 + enableIllustration 参数提交

### 步骤 7：前端 — draw.io 图表渲染
- 新建 `DrawioViewer` 组件（viewer JS 加载 + mxgraph DOM 渲染）
- 更新 `MilkdownEditorInner.tsx`：Crepe `featureConfigs` 注入 drawio 预览
- 更新 `MarkdownRenderer`：自定义 `code` 组件委托给 `DrawioViewer`

### 步骤 8：端到端验证

---

## 6. 边界情况与容错

| 场景 | 处理方式 |
|------|---------|
| enableIllustration=false（默认） | illustrator 输出被忽略，不发 draw.io 子会话，PhaseIndicator 仍显示 "识别配图" 但瞬间跳过 |
| illustrator 输出 `{"none":true}` | `consumeIllustratorLine` 直接 return |
| illustrator 输出非法 JSON | catch log.warn 跳过 |
| draw.io 子会话超时/失败 | catch log.error 跳过该图，不阻塞主流程 |
| anchor 在正文中找不到 | `injectIllustration` 降级追加到文末 |
| draw.io 返回空 XML | `generateIllustration` 返回 null，跳过注入 |
| 前端不传 enableIllustration（向后兼容） | `Boolean.TRUE.equals(null)` → false，走不配图链路 |

---

## 7. 测试验证清单

- [ ] enableIllustration=false：仅 3 阶段有效事件，illustrator 阶段瞬间闪过，落库无 drawio 代码块
- [ ] enableIllustration=true + 正文有配图点：4 阶段完整，illustrator 输出解析正确，draw.io 子会话生成 XML，XML 嵌入锚点后
- [ ] enableIllustration=true + 正文无配图点：illustrator 输出 `{"none":true}`，无子会话
- [ ] draw.io 子会话失败：主流程不阻塞，日志有 error，落库内容不含该图
- [ ] 前端 toggle 默认关闭
- [ ] 存量数据：旧 task 的 enable_illustration=0，行为同不配图模式
- [ ] DB 读写：enable_illustration 字段正确存取，PO/Entity 转换无损
- [ ] 编辑器渲染：Milkdown 中 ````drawio` 代码块渲染为交互式图表（缩放/平移可用）
- [ ] 文章页渲染：`MarkdownRenderer` 中 `DrawioViewer` 正确渲染图表
- [ ] viewer JS 加载失败：降级显示折叠面板，可展开查看 XML 源码
- [ ] 多图共存：一篇文中 1-3 张 drawio 图各自独立渲染，互不干扰
