# 项目进度分析与后续展开建议 — 2026-07-07

> 本文档沉淀当前项目实现进度、主要风险与后续建议。当前判断基于 `memory1.md`、一期工程设计文档、产品设计文档，以及现有前后端代码实现状态。

---

## 1. Alpha 的含义

`Alpha` 通常指一个功能或产品已经有了第一版可运行实现，但还没有达到稳定可交付状态。

在当前项目语境里，`AiWriting 模块 Alpha 版` 的意思是：

- 核心代码已经不是空壳，已经能看到完整链路雏形。
- 后端已有任务提交、任务查询、SSE 流式生成、任务落库等实现。
- 前端草稿编辑页已有 AI 写作按钮、流式接收、结果追加/替换/填充摘要等交互。
- 但这条链路还需要端到端联调、异常验证、状态语义修正、测试补齐和体验打磨。

也就是说：

```text
不是未开始，也不是正式稳定版，而是“第一版能跑的工程原型”。
```

---

## 2. 当前项目总体进度判断

当前项目已经从最初的 Agent 工具站雏形，推进到了：

```text
Agent 写作工作流 Alpha 版 + 内容发布闭环已打通
```

按照一期目标拆解，当前状态如下：

| 阶段 | 状态 | 说明 |
|------|------|------|
| M1 数据基线 | 已完成 | `user` / `draft` / `article` / `article_meta` / `ai_task` 表已设计，核心 DDL 已沉淀 |
| M2 内容域闭环 | 已完成 | 草稿、文章、发布、回草稿等核心内容链路已实现 |
| M3 Agent 写作链路 | Alpha 版已实现 | 已有 `AiWritingController`、`AiWritingService`、`AiTask` 持久化、前端 AI 面板 |
| M4 前端联调 | 部分完成 | 页面、API 模块和交互已具备，但需要完整端到端验证 |
| M5 社区能力 | 未开始 | 推荐流、点赞、收藏、评论等暂不建议优先做 |

---

## 3. 后端实现进度

### 3.1 内容域 Content 已较完整

当前 content 域已经具备比较完整的业务闭环：

- 草稿保存、查询、分页、废弃
- 草稿发布为文章
- 文章列表、文章详情
- 文章重新发布
- 文章回退草稿继续编辑
- 领域模型承载状态校验与业务行为

已落地的核心对象包括：

- `DraftEntity`
- `ArticleEntity`
- `ArticleMetaEntity`
- `ContentAggregate`
- `DraftDomainService`
- `ArticleDomainService`
- `PublishDomainService`
- `DraftRepository`
- `ArticleRepository`

这说明项目已经不只是 Controller + DAO 的 CRUD，而是有了较清晰的内容资产领域模型。

### 3.2 AiWriting 模块已有第一版链路

当前 AiWriting 已经不再是 memory1 中记录的“骨架占位”。代码中已经出现：

- `AiWritingController`
  - `POST /api/v1/ai-writing/task/submit`
  - `GET /api/v1/ai-writing/task/{taskId}`
  - `GET /api/v1/ai-writing/task/stream`
- `AiWritingService`
  - 创建 AI 写作任务
  - 读取草稿上下文
  - 构造写作 Prompt
  - 调用 `ChatService` 流式生成
  - 汇总结果并更新任务状态
- `AiTaskEntity`
- `AiWritingTaskTypeVO`
- `AiWritingStreamEventVO`
- `IAiTaskRepository`
- `AiTaskRepository`
- `IAiTaskDao`
- `AiTaskPO`

目前支持的写作任务包括：

- `GENERATE_OUTLINE`：生成大纲
- `GENERATE_BODY`：续写正文
- `POLISH_TEXT`：润色改写
- `SUMMARIZE`：生成摘要

---

## 4. 前端实现进度

前端已经具备一期主链路页面：

- `/` 工作台首页
- `/drafts` 草稿箱
- `/drafts/[draftId]` 草稿编辑页
- `/articles` 文章列表
- `/articles/[articleId]` 文章详情
- `/me` 个人中心

草稿编辑页已经实现了关键创作行为：

- 加载草稿详情
- 标题、正文、摘要、封面编辑
- 1.5 秒防抖自动保存
- 发布文章
- 提交 AI 写作任务
- 监听 AI 流式输出
- AI 结果追加到正文
- AI 结果替换正文
- AI 结果填充摘要
- 停止当前流式任务

这说明前端已经从“展示页面”推进到了“可创作工作台”的状态。

---

## 5. 当前主要风险

### 5.1 AiWriting 仍是 Alpha 状态

虽然链路已经有了，但还不能视为稳定版本。主要原因：

- 缺少完整端到端验证。
- 缺少 AI writing 单元测试和集成测试。
- 任务状态流转还需要进一步校正。
- 流式生成失败、客户端中断、模型异常等场景尚需验证。

### 5.2 任务状态语义存在隐患

当前提交任务时会直接创建运行中任务，但真正生成发生在 stream 接口调用阶段。

这可能导致：

```text
任务状态显示 RUNNING，但用户如果没有打开 stream，实际生成并没有开始。
```

更合理的状态流转应该是：

```text
PENDING -> RUNNING -> SUCCESS / FAILED
```

建议：

- `submitTask` 只创建 `PENDING` 任务。
- `stream` 开始时再标记为 `RUNNING`。
- 流式完成后标记为 `SUCCESS`。
- 异常时标记为 `FAILED` 并记录 `errorMsg`。

### 5.3 写作 Agent 配置存在硬编码

当前写作 Agent 固定为 `300002`。这适合演示阶段，但不利于后续扩展：

- 多写作 Agent
- 多模型策略
- 不同任务类型绑定不同 Agent
- 用户自定义模型配置

建议后续将写作 Agent 选择规则配置化。

### 5.4 流式执行与任务生命周期耦合较紧

当前 `AiWritingService.generateStream` 同时承担：

- 校验任务
- 创建 Agent session
- 调用流式模型
- 拼接输出
- 推送事件
- 更新任务状态

短期可以接受，但后续如果要支持：

- 取消任务
- 重试任务
- 异步队列
- 多任务并发
- 断线恢复

就需要进一步拆分职责。

### 5.5 用户体系仍是演示态

当前多个 Controller 仍使用固定用户：

```text
DEFAULT_USER_ID = 1L
```

这意味着项目目前仍偏单用户 demo。后续如果要变成真实产品，需要补齐：

- 用户登录接口
- 当前用户识别
- 用户权限校验
- 作者信息查询
- 草稿和文章的多用户隔离

### 5.6 前端草稿编辑页逐渐变大

草稿编辑页已经承担了很多职责：

- 登录检查
- 草稿加载
- 自动保存
- AI 任务提交
- SSE 流处理
- AI 结果采纳
- 发布弹窗
- 页面渲染

后续继续叠加功能会变难维护，建议尽快组件化。

---

## 6. 产品方向建议

结合当前项目状态和新的产品判断，建议后续主方向调整为：

```text
Agent 驱动的技术写作工作台
```

而不是优先强调：

```text
技术社区平台
```

更合理的产品主线是：

```text
Agent 对话创作 -> 草稿沉淀 -> 文章发布 -> 内容展示 -> 社区传播
```

这里的主次关系应该是：

| 层级 | 定位 |
|------|------|
| Agent 对话 | 核心体验 |
| 草稿编辑 | 创作过程工作区 |
| 文章发布 | 创作结果固化 |
| 技术社区 | 内容展示、传播、沉淀 |
| 个人中心 | 创作资产管理 |

这样项目会更有辨识度。否则如果过早强调社区能力，容易变成一个普通博客系统，只是旁边挂了几个 AI 按钮。

---

## 7. 后续展开优先级建议

### 第一优先级：稳定 AiWriting 链路

目标：让 Agent 写作链路真正稳定可用。

建议任务：

1. 修正任务状态流转：`PENDING -> RUNNING -> SUCCESS / FAILED`
2. stream 开始时再标记运行中
3. 失败时完整记录 `errorMsg`
4. 客户端中断时保证后端不抛无意义异常
5. 补齐 `AiTaskEntity`、`AiWritingService`、`AiTaskRepository` 测试
6. 用真实浏览器跑通：生成大纲、续写、润色、摘要
7. 验证 `ai_task` 表落库字段是否正确

### 第二优先级：升级 Agent 创作体验

目标：从“按钮式 AI 生成”升级为“Agent 写作工作台”。

建议任务：

1. 支持用户输入自定义写作指令
2. 支持基于选中文本润色
3. 支持生成标题、生成标签、发布质量检查
4. 支持任务历史记录展示
5. 支持重新应用上一次 AI 生成结果
6. 支持 Agent 结果按块展示，而不是单一文本 buffer

### 第三优先级：前端草稿编辑页组件化

目标：降低编辑页复杂度，为后续扩展 Agent 工作台做准备。

建议拆分：

```text
DraftEditorPage
DraftToolbar
MarkdownEditor
AiWritingPanel
AiResultActions
PublishDialog
DraftMetaPanel
```

组件化之后，再继续增加 AI 任务历史、对话输入框、Markdown 预览等功能会更稳。

### 第四优先级：补真实用户体系

目标：从单用户 demo 过渡到真实平台雏形。

建议任务：

1. 增加 `UserEntity`
2. 增加 `IUserDao` / `UserPO`
3. 增加 `UserRepository`
4. 增加当前用户查询接口
5. Controller 去掉 `DEFAULT_USER_ID`
6. 文章详情补作者名、头像等信息

### 第五优先级：再做社区能力

技术社区建议作为内容结果层，不要现在抢主线。

后续可以逐步补：

- 标签筛选
- 文章推荐流
- 阅读量排序
- 点赞
- 收藏
- 评论
- 作者主页

---

## 8. 推荐阶段路线

```text
阶段 A：AiWriting 稳定化
  -> 修状态、补测试、跑通真实流式生成

阶段 B：Agent 写作体验增强
  -> 自定义指令、选中文本处理、任务历史、质量检查

阶段 C：草稿编辑页工程化
  -> 组件拆分、状态收口、预览能力增强

阶段 D：用户体系补齐
  -> 去除固定用户、补作者信息、多用户隔离

阶段 E：社区能力扩展
  -> 推荐、标签、点赞、收藏、评论
```

---

## 9. 当前最推荐立即做的事

下一步最建议优先做：

```text
AiWriting 链路稳定化 + 前后端真实联调
```

不要马上做社区，也不要先做大量 UI 美化。当前项目的最大差异化在 Agent 写作体验，应该先把这条主线做实。

一句话总结：

```text
内容域已经把“文章沉淀”托住了，下一步应该把 Agent 创作这件事打磨成项目的核心体验。
```
