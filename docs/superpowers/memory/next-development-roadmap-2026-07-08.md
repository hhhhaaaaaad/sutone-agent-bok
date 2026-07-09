# 后续项目展开路线详案 — 2026-07-08

> 本文档基于当前项目实现状态、`project-progress-analysis-2026-07-07.md`、一期工程方案，以及最新产品方向判断整理。目标是明确后续如何把项目从“单用户 Demo + Agent 写作 Alpha”推进为“可稳定演示、可继续扩展的 Agent 技术写作工作台”。

---

## 1. 当前判断

当前项目已经完成了第一期最核心的内容创作闭环：

```text
草稿编辑 -> Agent 写作辅助 -> AI 结果采纳 -> 自动保存 -> 发布文章 -> 文章展示
```

但它还没有完全成为一个真实产品形态，主要原因是：

1. 用户体系仍是前端演示态，后端多个 Controller 仍使用 `DEFAULT_USER_ID = 1L`。
2. `/me` 个人中心主要是前端页面壳子，用户资料来自前端 cookie，不来自后端。
3. AiWriting 主体能力已经完成，但还需要异常收口、端到端验证和结果展示优化。
4. 草稿编辑页能力越来越多，需要进一步组件化和 hooks 化，避免后续不可维护。
5. 技术社区能力目前只适合作为后续扩展，不应抢占当前主线。

因此，后续主线建议是：

```text
真实用户与个人中心后端化
  -> AiWriting 稳定性收口
  -> 草稿编辑页工程化
  -> Agent 写作工作台体验增强
  -> 技术社区能力扩展
```

---

## 2. 产品定位调整

原始方案偏向：

```text
AI 技术知识生产与社区平台
```

当前更建议收敛为：

```text
Agent 驱动的技术写作工作台
```

技术社区不是不要，而是作为内容结果层存在：

| 层级 | 定位 | 当前优先级 |
|------|------|------------|
| Agent 写作 | 核心体验 | 最高 |
| 草稿编辑 | 创作工作区 | 最高 |
| 文章发布 | 内容结果固化 | 已完成主体 |
| 个人中心 | 创作资产管理 | 下一步重点 |
| 技术社区 | 内容传播与互动 | 后置 |

这可以避免项目变成普通博客系统，同时把已有 Agent 能力作为真正差异化。

---

## 3. 阶段一：真实用户体系与 admin 个人中心后端化

### 3.1 阶段目标

把当前前端硬编码的 `admin/admin` 和后端 `DEFAULT_USER_ID = 1L`，改造成后端可查询、可登录、可识别当前用户的最小用户体系。

目标不是一次性做复杂权限系统，而是完成第一版真实用户闭环：

```text
登录 -> 后端校验用户 -> 前端保存登录态 -> 请求携带用户身份 -> 后端识别当前用户 -> /me 展示真实用户资料与个人内容
```

### 3.2 当前问题

当前登录和个人中心状态：

```text
/login 前端硬编码 admin/admin
/me 从 cookie 读取 user 字符串
后端没有登录接口
后端没有 current user 接口
后端没有 UserEntity / UserRepository
draft/article controller 使用 DEFAULT_USER_ID = 1L
```

这导致：

- 前端显示的 admin 不是后端认证结果。
- `user` 表虽然存在，但 Java 代码没有使用。
- 草稿归属和文章作者无法真实多用户隔离。
- 文章列表中的 `authorName`、`avatarUrl` 不能正确返回。

### 3.3 后端建议新增内容

建议新增 `account` 或 `user` 相关包。为了贴合现有 DDD 风格，推荐放在 `domain.account`，但第一版也可以轻量实现。

推荐后端结构：

```text
sutone-agent-bok-api
└── cn.sutone.ai.api
    ├── IAuthService.java
    ├── IUserService.java
    └── dto/user
        ├── LoginRequestDTO.java
        ├── LoginResponseDTO.java
        └── CurrentUserResponseDTO.java

sutone-agent-bok-trigger
└── cn.sutone.ai.trigger.http
    ├── AuthController.java
    └── UserController.java

sutone-agent-bok-domain
└── cn.sutone.ai.domain.account
    ├── model/entity/UserEntity.java
    ├── repository/IUserRepository.java
    └── service/UserDomainService.java

sutone-agent-bok-infrastructure
└── cn.sutone.ai.infrastructure
    ├── dao/IUserDao.java
    ├── dao/po/UserPO.java
    └── adapter/repository/UserRepository.java
```

### 3.4 建议接口

#### 1. 登录接口

```text
POST /api/v1/auth/login
```

请求：

```json
{
  "username": "admin",
  "password": "admin"
}
```

响应：

```json
{
  "code": "0000",
  "info": "success",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickname": "苏东昊",
    "avatarUrl": null,
    "token": "demo-token-or-session-id"
  }
}
```

第一版可以先用简单 token，不必立刻接 JWT。比如：

```text
sutone-demo-token-{userId}-{timestamp}
```

后续再替换为 JWT 或 Spring Security。

#### 2. 当前用户接口

```text
GET /api/v1/users/current
```

响应：

```json
{
  "code": "0000",
  "info": "success",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickname": "苏东昊",
    "avatarUrl": null
  }
}
```

#### 3. 我的草稿接口

当前已有：

```text
GET /api/v1/drafts/page
```

可以继续复用，但后端不再使用 `DEFAULT_USER_ID`，而是从请求中解析当前用户。

#### 4. 我的文章接口

建议新增：

```text
GET /api/v1/me/articles/page
```

或者扩展现有文章分页：

```text
GET /api/v1/articles/page?scope=mine
```

第一版更推荐新增 `/me/articles/page`，语义更清晰。

### 3.5 后端改造重点

1. 增加 `UserPO` 映射 `user` 表。
2. 增加 `IUserDao.queryByUsername` 和 `queryById`。
3. 增加 `UserRepository` 完成 PO 与 Entity 转换。
4. 增加 `UserDomainService.login` 和 `queryCurrentUser`。
5. Controller 中移除 `DEFAULT_USER_ID = 1L`。
6. 增加统一获取当前用户 ID 的方法。
7. `ArticleRepository` 或查询层补作者信息。
8. 文章列表和详情返回真实 `authorName`、`avatarUrl`。

### 3.6 前端改造重点

当前前端登录页硬编码 admin，需要改为调用后端：

```text
src/api/auth.ts
```

建议新增：

```ts
login(username, password)
getCurrentUser()
logout()
```

`utils/cookie.ts` 建议从只保存：

```ts
{ user, ts }
```

改为保存：

```ts
{
  token: string;
  userId: number;
  username: string;
  nickname: string;
  avatarUrl?: string;
  ts: number;
}
```

`/me` 页面改造：

1. 页面初始化时调用 `users/current`。
2. 顶部用户信息使用后端返回的 nickname/avatarUrl。
3. 最近草稿继续调 `drafts/page`。
4. 最近文章改为调“我的文章”接口。
5. 未登录时跳转 `/login`。

### 3.7 验收标准

该阶段完成后，应满足：

1. `admin/admin` 由后端校验，不再由前端硬编码判断。
2. `/me` 页面展示的昵称来自 `user` 表中的 `nickname`。
3. 后端 Controller 不再出现 `DEFAULT_USER_ID = 1L`。
4. 草稿列表只返回当前登录用户的草稿。
5. 我的文章只返回当前登录用户发布的文章。
6. 文章详情能展示真实作者昵称。
7. 刷新页面后登录态仍能恢复。

---

## 4. 阶段二：AiWriting 稳定性收口

### 4.1 阶段目标

当前 AiWriting 已经有主体实现，下一步重点不是继续加功能，而是稳定核心链路。

目标链路：

```text
提交 AI 任务
  -> 创建 PENDING 任务
  -> stream 开始后 RUNNING
  -> 流式输出 token
  -> 前端实时展示
  -> 成功后 SUCCESS + responseContent 落库
  -> 失败后 FAILED + errorMsg 落库
  -> 前端可查询任务历史并重新应用结果
```

### 4.2 当前已完成内容

已完成主体能力：

- `AiWritingController`
- `AiWritingService`
- `AiTaskEntity`
- `AiWritingTaskTypeVO`
- `AiTaskRepository`
- `IAiTaskDao`
- 前端 `AiWritingPanel`
- AI 任务历史展示
- 自定义写作指令
- 选中文本润色
- 生成标题、标签、摘要、正文、大纲
- 发布质量检查

### 4.3 待收口问题

#### 1. 前置异常失败落库

当前需要重点确认：

- Agent 配置缺失
- MCP 鉴权失败
- 创建 session 失败
- 模型 API Key 无效
- stream 建立失败

这些异常是否都会把任务标记为 `FAILED`。

如果异常发生在任务开始前，但任务已经创建，就必须落库：

```text
status = FAILED
error_msg = 具体失败原因
```

#### 2. 客户端断开处理

当前前端可以停止生成，但后端是否真正停止模型调用需要确认。

第一版可以接受：

```text
客户端断开 -> 后端捕获 IOException -> 不打印 ERROR 噪音 -> 当前任务按 FAILED 或 CANCELLED 处理
```

如果要更完整，可以新增状态：

```text
CANCELLED
```

但第一版不是必须。

#### 3. 端到端验证

建议沉淀一份人工验证清单：

```text
1. 登录 admin
2. 新建草稿
3. 输入标题和正文
4. 生成大纲
5. 追加到正文
6. 生成摘要
7. 填充摘要
8. 选中一段文本润色
9. 发布文章
10. 查看文章详情
11. 回到 /me 查看最近内容
12. 查询 ai_task 表确认状态和结果
```

### 4.4 建议补充测试

如果当前已有测试，则重点补异常测试：

1. `submitTask_shouldCreatePendingTask`
2. `generateStream_shouldMarkRunningThenSuccess`
3. `generateStream_shouldMarkFailedWhenModelThrows`
4. `generateStream_shouldIgnoreToolCallEvents`
5. `queryTask_shouldRejectOtherUserTask`
6. `AiTaskRepository_shouldMapAllFields`

### 4.5 验收标准

1. 任务状态流转正确。
2. 成功和失败都能正确落库。
3. 前端所有 AI 动作可以真实跑通。
4. MCP/API Key 异常不会导致任务永远 RUNNING。
5. 日志中不出现大量无意义 stacktrace。
6. 任务历史可以重新应用历史结果。

---

## 5. 阶段三：草稿编辑页工程化

### 5.1 阶段目标

当前草稿编辑页已经承载很多职责。后续继续扩展前，应先拆分组件和 hooks。

目标：

```text
页面负责组合
组件负责展示
hooks 负责状态和副作用
api 模块负责请求
```

### 5.2 建议组件拆分

推荐目录：

```text
src/components/draft-editor
├── DraftEditorPage.tsx
├── DraftToolbar.tsx
├── MarkdownEditor.tsx
├── MarkdownPreview.tsx
├── AiWritingPanel.tsx
├── AiResultActions.tsx
├── DraftMetaPanel.tsx
└── PublishDialog.tsx

src/hooks
├── useDraftEditor.ts
├── useAutoSave.ts
├── useAiWritingTask.ts
└── useCurrentUser.ts
```

如果现有组件已经放在 `components/AiWritingPanel`、`components/DraftMetaPanel`，可以先保留，再逐步收敛到 `components/draft-editor`。

### 5.3 hooks 职责建议

#### 1. `useDraftEditor`

负责：

- 加载草稿详情
- 管理 title/content/summary/coverUrl
- 处理错误和 loading
- 暴露 update 方法

#### 2. `useAutoSave`

负责：

- 监听草稿变化
- 1.5 秒防抖保存
- 保存中/已保存/保存失败状态
- 连续输入时避免请求重叠

#### 3. `useAiWritingTask`

负责：

- 提交 AI 任务
- 建立 stream
- 处理 token/status/done/error
- 停止生成
- 查询任务历史
- 应用历史结果

#### 4. `useCurrentUser`

负责：

- 读取本地登录态
- 请求当前用户
- 未登录跳转
- 退出登录

### 5.4 验收标准

1. `/drafts/[draftId]/page.tsx` 只保留页面组合逻辑。
2. 自动保存逻辑不直接散落在页面 JSX 中。
3. AI 流式状态由 hook 管理。
4. 发布弹窗组件独立。
5. Markdown 编辑器可以后续独立扩展预览模式。
6. 页面功能不回退。

---

## 6. 阶段四：Agent 写作工作台体验增强

### 6.1 阶段目标

在稳定和工程化之后，把当前“按钮式 AI 生成”升级为更完整的 Agent 写作工作台。

### 6.2 建议增强能力

#### 1. Markdown 编辑/预览切换

支持：

```text
编辑模式 / 预览模式 / 分屏模式
```

这会明显提升写作体验。

#### 2. AI 结果结构化卡片

当前 AI 结果主要是一个文本 buffer。后续可以按任务类型展示不同结果块：

| 任务类型 | 展示形态 |
|----------|----------|
| 生成标题 | 标题候选列表 |
| 生成摘要 | 摘要卡片 |
| 生成标签 | 标签 chips |
| 续写正文 | Markdown 内容块 |
| 润色改写 | 对比视图或替换块 |
| 质量检查 | 检查报告列表 |

#### 3. 发布质量检查

建议输出结构化结果：

```text
标题质量
摘要质量
结构完整性
Markdown 格式
技术准确性风险
建议修改点
```

第一版可先让模型输出 Markdown，后续再做 JSON 结构化。

#### 4. 任务历史增强

当前已有任务历史后，可以进一步支持：

- 按任务类型筛选
- 显示任务状态
- 显示生成时间
- 重新应用结果
- 删除历史任务
- 失败任务重试

#### 5. AI 采纳结果可撤销

建议前端保留一次采纳前快照：

```text
apply AI result -> 保存 previousContent -> 可撤销
```

### 6.3 验收标准

1. 用户能用自定义指令驱动写作。
2. 用户能基于选中文本局部润色。
3. AI 结果不再只有一坨文本，而是按任务更合理展示。
4. 质量检查能帮助发布前修改文章。
5. 采纳 AI 结果后可以撤销。

---

## 7. 阶段五：技术社区能力扩展

### 7.1 阶段目标

等 Agent 写作工作台稳定后，再补内容分发与互动能力。

技术社区是结果层，不是当前主线。

### 7.2 推荐顺序

#### 1. 标签筛选

基于已存在 tags 字段，先支持：

```text
/articles?tag=Java
```

#### 2. 作者主页

新增：

```text
/users/{userId}
/users/{userId}/articles
```

#### 3. 阅读量排序

支持：

```text
/articles/page?sort=latest
/articles/page?sort=views
```

#### 4. 点赞和收藏

建议新增表：

```text
article_like
article_favorite
```

#### 5. 评论

建议新增：

```text
comment
```

第一版支持一级评论即可，暂不做楼中楼。

#### 6. 推荐流

第一版不做复杂算法，可以按：

```text
发布时间 + 阅读量 + 点赞数
```

做简单排序。

### 7.3 验收标准

1. 文章可以按标签筛选。
2. 用户可以查看作者主页。
3. 用户可以点赞、收藏文章。
4. 用户可以评论文章。
5. 首页推荐不再只是最新列表。

---

## 8. 推荐实施顺序

如果按最稳妥方式推进，建议这样排：

```text
第 1 步：真实用户体系与个人中心后端化
第 2 步：AiWriting 异常收口与端到端验证
第 3 步：草稿编辑页组件化和 hooks 化
第 4 步：Agent 写作结果结构化展示
第 5 步：Markdown 预览与编辑体验增强
第 6 步：作者主页和标签筛选
第 7 步：点赞、收藏、评论
```

如果按最能展示产品亮点的方式推进，可以这样排：

```text
第 1 步：AiWriting 端到端稳定验证
第 2 步：AI 结果结构化卡片
第 3 步：Markdown 编辑/预览切换
第 4 步：真实用户体系
第 5 步：个人中心后端化
第 6 步：社区能力
```

但从工程基础角度，我更推荐第一种。

---

## 9. 当前最建议立即执行的任务包

### 任务包 A：用户体系最小闭环

范围：

```text
UserEntity + UserPO + IUserDao + UserRepository + Login API + Current User API + 前端登录改造
```

交付结果：

```text
admin 登录由后端校验，/me 显示后端用户资料，后端不再依赖 DEFAULT_USER_ID。
```

### 任务包 B：个人中心后端化

范围：

```text
/me 页面改造 + 我的草稿 + 我的文章 + 作者信息返回
```

交付结果：

```text
个人中心成为真实用户资产页，而不是前端 cookie 展示页。
```

### 任务包 C：AiWriting 稳定性验收

范围：

```text
异常落库 + 浏览器端到端验证 + MCP/API Key 错误兜底 + 任务历史验证
```

交付结果：

```text
Agent 写作链路可以稳定演示，不会因为外部服务异常导致任务悬挂。
```

---

## 10. 最终建议

当前项目最不缺的是“再加一个功能按钮”，最需要的是把已有能力做成一个可信产品闭环。

因此下一阶段最推荐的主题是：

```text
真实用户体系 + 个人中心后端化 + AiWriting 稳定验收
```

完成这三件事后，项目会从：

```text
单用户 Demo + AI 写作能力展示
```

升级为：

```text
有用户归属、有内容资产、有 Agent 写作核心体验的技术写作工作台
```

这会为后续继续做社区、推荐、互动和知识沉淀打下更稳的基础。
