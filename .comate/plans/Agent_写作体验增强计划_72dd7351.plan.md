# Agent 写作体验增强计划（阶段 B）

## 目标

从"按钮式 AI 生成"升级为"Agent 写作工作台"。

---

## 变更概览

```
后端：5 个文件（3 修改 + 2 新建）
前端：8 个文件（5 新建 + 3 修改）
```

---

## 1. 支持自定义写作指令

### 现状
- 当前 4 个固定按钮，只能选择预设任务类型
- `promptParams` 仅传 `{ title }`，用户无法输入指令

### 改动

**后端** — `AiWritingService.java`
- `buildPrompt()` 方法中接收 `promptParams` 的 `customInstruction` 字段，追加到 prompt 末尾

**前端** — `AiWritingPanel` 组件（新建）
- 从 `page.tsx` 拆出 AI 面板为独立组件
- 在按钮上方增加 `<textarea>` 输入框，用户可输入自定义写作指令
- `handleAiTask()` 将自定义指令作为 `promptParams.customInstruction` 发送给后端
- 该指令影响所有 4 种任务类型的输出风格

---

## 2. 支持基于选中文本润色

### 现状
- 编辑器的 `<textarea>` 没有 `ref`，无法获取选中的文本
- `POLISH_TEXT` 时只能处理全量正文

### 改动

**前端** — `DraftEditorPage` + `AiWritingPanel`
- 为 `<textarea>` 添加 `ref={contentRef}`（已有 `contentRef` 但未绑定到 DOM）
- 新增 `selectedText` state，通过 `onMouseUp` 或 `onSelect` 事件捕获 `selectionStart/selectionEnd`
- `AiWritingPanel` 中增加一个按钮"润色选中文本"，选中文本时亮起，未选中时置灰
- 提交任务时将选中文本作为 `promptParams.selectedText` 传入

**后端** — `AiWritingService.java`
- `buildPrompt(POLISH_TEXT)` 中：如果 `selectedText` 非空，prompt 改为"请对以下选中文本进行润色"，只传入选中的文本而非全文

---

## 3. 新增任务类型：生成标题 / 生成标签 / 发布质量检查

### 新增任务类型

| 任务类型 | 说明 | Prompt |
|---------|------|--------|
| `GENERATE_TITLE` | 基于正文生成 3-5 个候选标题 | 基于正文，生成吸引技术读者的标题 |
| `GENERATE_TAGS` | 基于正文生成相关标签 | 分析正文，生成 3-5 个技术标签 |
| `QUALITY_CHECK` | 检查文章是否符合发布质量标准 | 检查拼写、结构完整性、代码正确性 |

### 后端改动

**`AiWritingTaskTypeVO.java`** — 增加 3 个枚举值
```java
GENERATE_TITLE("GENERATE_TITLE", "生成标题"),
GENERATE_TAGS("GENERATE_TAGS", "生成标签"),
QUALITY_CHECK("QUALITY_CHECK", "发布质量检查");
```

**`AiWritingService.java`** — `buildPrompt()` switch 增加 3 个 case

### 前端改动

**`AiWritingPanel`** — 按钮区调整
- 将 4 个按钮拆为两组：
  - **写作组**：生成大纲、续写正文、润色改写
  - **辅助组**：生成标题、生成标签、质量检查、生成摘要
- 支持折叠/展开辅助组（默认折叠）

---

## 4. 前端草稿编辑页组件化

作为增强体验的基础工程，将 `page.tsx` 拆分为独立组件。

| 新组件 | 文件 | 说明 |
|--------|------|------|
| `AiWritingPanel` | `src/components/AiWritingPanel/index.tsx` | AI 任务提交、SSE 流接收、结果展示、指令输入、选中文本润色、任务历史 |
| `PublishDialog` | `src/components/PublishDialog/index.tsx` | 发布弹窗（独立出来可复用） |
| `DraftMetaPanel` | `src/components/DraftMetaPanel/index.tsx` | 右侧面板的摘要、封面 URL |

---

## 5. 任务历史记录 + 重新应用结果

### 后端

**`IAiTaskDao.java`** — 新增按 draftId 查询最近任务列表
```sql
SELECT * FROM ai_task WHERE draft_id = #{draftId} AND is_deleted = 0 ORDER BY create_time DESC LIMIT #{limit}
```

**`IAiTaskRepository.java` / `AiTaskRepository.java`** — 新增 `queryLatestByDraftId(draftId, limit)`

**`AiWritingController.java`** — 新增端点
```
GET /api/v1/ai-writing/task/list?draftId=xxx
```

### 前端

**`AiWritingPanel`** — 底部增加任务历史区域
- 加载草稿时一并拉取历史任务列表
- 按时间倒序展示最近 5 条记录
- 每条记录显示：任务类型、时间、状态
- 点击"重新应用"可再次应用该次生成结果

---

## 6. Agent 结果按块展示

### 当前问题
- AI 结果以 `aiResultBuffer` 单一字符串展示在固定区域
- 无法看到任务执行过程的不同阶段输出

### 改动

**`AiWritingPanel`** — 结果展示区域改为多块卡片视图
- 每次新任务产生一个结果卡片
- 卡片标题：任务类型描述 + 时间
- 卡片内容：流式输出的完整内容（只读预览，支持滚动）
- 卡片操作：追加正文、替换正文、回填摘要、清空（与当前功能一致）
- 任务历史记录复用同样的卡片视图

---

## 改动文件清单

### 后端 (5 文件)

| 文件 | 操作 | 说明 |
|------|------|------|
| `...AiWritingTaskTypeVO.java` | 修改 | 增加 GENERATE_TITLE/GENERATE_TAGS/QUALITY_CHECK 枚举 |
| `...AiWritingService.java` | 修改 | buildPrompt 增加 3 个 case + 支持 customInstruction/selectedText |
| `...AiWritingController.java` | 修改 | 新增 /task/list 端点 |
| `...IAiTaskDao.java` | 修改 | 新增 queryByDraftId 方法 |
| `...AiTaskRepository.java` | 修改 | 新增 queryLatestByDraftId 方法 |
| `...IAiTaskRepository.java` | 修改 | 新增 queryLatestByDraftId 接口方法 |

### 前端 (8 文件)

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/components/AiWritingPanel/index.tsx` | 新建 | AI 面板组件（含自定义指令输入、选中文本润色、结果卡片、任务历史） |
| `src/components/PublishDialog/index.tsx` | 新建 | 发布弹窗组件 |
| `src/components/DraftMetaPanel/index.tsx` | 新建 | 右侧摘要/封面面板 |
| `src/app/drafts/[draftId]/page.tsx` | 修改 | 拆分组件、绑定 textarea ref、选中文本监听 |
| `src/types/ai-writing.ts` | 修改 | 增加 AiTaskType 新枚举值 |
| `src/api/ai-writing.ts` | 修改 | 增加 queryTaskList API |

---

## 项目结构变化

```
src/
  components/
    AiWritingPanel/
      index.tsx         # AI 面板（新）
    PublishDialog/
      index.tsx         # 发布弹窗（新）
    DraftMetaPanel/
      index.tsx         # 草稿元信息面板（新）
```

---

## 不做范围

- 不做多写作 Agent 选择（WRITING_AGENT_ID 硬编码问题，属于后续技术债清理）
- 不做流式执行与任务生命周期解耦（后续再考虑异步队列）
- 不做用户体系变更（属于阶段 D）
