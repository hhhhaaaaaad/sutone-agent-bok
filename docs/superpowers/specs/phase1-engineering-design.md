# AI技术知识生产与社区平台 - 第一期工程方案设计文档

> **文档说明**：
> 本文档作为第一阶段（AI 创作发布中心）的活文档（Living Document）。
> 后续所有关于数据库设计、API 契约、前端路由、后端领域层重构的详细方案，均会追加并更新至本文档中。

## 1. 第一期项目概述
- **核心定位**：AI 技术知识生产与社区平台（聚焦“创作-发布”闭环）
- **核心链路**：`登录 -> AI 创作 -> 草稿编辑 -> 发布 -> 文章展示 -> 个人中心管理`
- **暂缓内容**：Draw.io 图表深度集成、复杂社区互动（点赞/评论/收藏/关注）。

## 2. 数据库表结构设计

> **设计原则**：
> 1. **草稿与文章分离**：`draft` 承载创作态，`article` 承载发布态。解耦高频编辑与前台展示。
> 2. **AI 能力业务化**：引入 `ai_task` 表，将大模型调用转化为平台资产，为后续可观测性打底。
> 3. **企业级规约**：统一使用自增 ID 或 Snowflake ID（本期使用 bigint），统一包含 `create_time`、`update_time`、`is_deleted`。

### 2.1 用户表 (`user`)
轻量级用户体系，后续可扩展微信扫码或三方登录。

```sql
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password_hash` varchar(128) NOT NULL COMMENT '密码哈希值(演示可明文/弱哈希)',
  `nickname` varchar(64) DEFAULT NULL COMMENT '用户昵称',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除 0-否 1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 2.2 草稿表 (`draft`)
面向创作者的高频编辑对象，可以被 AI 任务反复修改。

```sql
CREATE TABLE `draft` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '草稿ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `title` varchar(128) DEFAULT NULL COMMENT '草稿标题',
  `content_md` longtext COMMENT 'Markdown格式正文',
  `summary` varchar(512) DEFAULT NULL COMMENT '摘要草稿',
  `cover_url` varchar(255) DEFAULT NULL COMMENT '封面图URL',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '草稿状态: 0-编辑中, 1-已发布, 2-已废弃',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容创作草稿表';
```

### 2.3 文章表 (`article`)
对外发布的静态快照，生成后供前台展示。

```sql
CREATE TABLE `article` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  `draft_id` bigint(20) NOT NULL COMMENT '来源草稿ID(保留追溯关系)',
  `author_id` bigint(20) NOT NULL COMMENT '作者用户ID',
  `title` varchar(128) NOT NULL COMMENT '文章标题',
  `content_md` longtext NOT NULL COMMENT 'Markdown格式正文',
  `content_html` longtext COMMENT 'HTML格式正文(可选,用于前端直接渲染)',
  `summary` varchar(512) DEFAULT NULL COMMENT '文章摘要',
  `cover_url` varchar(255) DEFAULT NULL COMMENT '封面图',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '发布状态: 0-下线, 1-正常可见',
  `publish_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_publish_time` (`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式发布文章表';
```

### 2.4 文章元信息表 (`article_meta`)
剥离高频更新的统计数据，以及后续社区标签。

```sql
CREATE TABLE `article_meta` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `article_id` bigint(20) NOT NULL COMMENT '文章ID',
  `word_count` int(11) NOT NULL DEFAULT '0' COMMENT '文章字数',
  `view_count` int(11) NOT NULL DEFAULT '0' COMMENT '阅读量',
  `like_count` int(11) NOT NULL DEFAULT '0' COMMENT '点赞数(预留)',
  `tags` varchar(255) DEFAULT NULL COMMENT 'JSON格式标签列表',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章元数据与统计表';
```

### 2.5 AI 任务记录表 (`ai_task`)
将 Agent 生成过程记录落库，这不仅是为了当前能显示“最近生成结果”，更是为了未来向“异步队列、大模型消耗审计、流式失败重试”架构演进。

```sql
CREATE TABLE `ai_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '发起用户ID',
  `draft_id` bigint(20) DEFAULT NULL COMMENT '关联的草稿ID',
  `task_type` varchar(32) NOT NULL COMMENT '任务类型: GENERATE_OUTLINE, GENERATE_BODY, POLISH_TEXT 等',
  `prompt_payload` text COMMENT '发给大模型的完整提示词或请求JSON',
  `response_content` longtext COMMENT '大模型返回的原始结果',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '任务状态: 0-进行中, 1-成功, 2-失败',
  `error_msg` varchar(512) DEFAULT NULL COMMENT '失败原因记录',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_draft_id` (`draft_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 创作任务记录表';
```

## 3. 前后端 API 接口契约

> **设计规约**：
> 1. **统一返回体**：所有非流式接口均使用 `Response<T>` 包装，包含 `code`, `info`, `data`。
> 2. **RESTful 风格**：遵循资源导向的路径设计（如 `/api/v1/drafts`）。
> 3. **流式交互**：AI 生成接口采用 SSE (Server-Sent Events) 返回 `text/event-stream`，解决长文本生成的超时和用户体验问题。
> 4. **前端友好**：返回结构尽量直接贴合前端页面消费，避免前端自行拼装过多派生字段。

### 3.0 通用返回结构

前端当前已经在 [api.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/types/api.ts) 中使用如下通用结构，因此内容平台接口建议保持一致：

```json
{
  "code": "0000",
  "info": "success",
  "data": {}
}
```

对应 TypeScript 类型：

```ts
export interface Response<T> {
  code: string;
  info: string;
  data: T;
}
```

分页返回统一建议为：

```json
{
  "total": 42,
  "pageNo": 1,
  "pageSize": 10,
  "list": []
}
```

### 3.1 草稿模块 (Draft)

#### 1. 保存/更新草稿
- **接口路径**：`POST /api/v1/drafts/save`
- **业务逻辑**：如果传了 `draftId` 则是更新，没传则是新建。前端实现自动保存（Auto-Save）时高频调用。
- **请求参数** (Body):
  ```json
  {
    "draftId": 1001, // 可选
    "title": "计算机操作系统中的信号量",
    "contentMd": "## 什么是信号量\n...",
    "summary": "简述信号量机制",
    "coverUrl": "https://..."
  }
  ```
- **响应参数** (Data):
  ```json
  {
    "draftId": 1001,
    "title": "计算机操作系统中的信号量",
    "status": 0,
    "lastUpdateTime": "2026-07-04 15:30:00"
  }
  ```

- **前端用途**：
  - 新建草稿时拿到 `draftId` 后跳转 `/drafts/{draftId}`
  - 自动保存后刷新“已保存”状态
  - 若后端对标题做了裁剪或默认填充，前端可直接使用返回值回填

#### 2. 获取草稿详情
- **接口路径**：`GET /api/v1/drafts/{draftId}`
- **业务逻辑**：草稿编辑页初始化时获取完整内容。这个接口是前端编辑页真正稳定落地所必需的。
- **响应参数** (Data):
  ```json
  {
    "draftId": 1001,
    "userId": 20001,
    "title": "计算机操作系统中的信号量",
    "contentMd": "## 什么是信号量\n信号量（Semaphore）本质上是...",
    "summary": "简述信号量机制",
    "coverUrl": "https://...",
    "status": 0,
    "statusDesc": "编辑中",
    "createTime": "2026-07-04 14:00:00",
    "updateTime": "2026-07-04 15:30:00"
  }
  ```

- **前端用途**：
  - 编辑页首次加载标题、正文、摘要、封面
  - 判断草稿当前是否仍可继续编辑
  - 解决“刷新编辑页后无详情数据”的问题

#### 3. 获取草稿列表 (分页)
- **接口路径**：`GET /api/v1/drafts/page`
- **请求参数** (Query):
  - `pageNo`: 当前页码 (默认 1)
  - `pageSize`: 每页条数 (默认 10)
- **响应参数** (Data):
  ```json
  {
    "total": 42,
    "pageNo": 1,
    "pageSize": 10,
    "list": [
      {
        "draftId": 1001,
        "title": "计算机操作系统中的信号量",
        "summary": "简述信号量机制",
        "status": 0,
        "statusDesc": "编辑中",
        "coverUrl": "https://...",
        "updateTime": "2026-07-04 15:30:00"
      }
    ]
  }
  ```

- **前端用途**：
  - `/drafts` 草稿箱分页列表
  - `/` 工作台首页“继续最近草稿”模块

#### 4. 废弃/删除草稿（可选但建议补齐）
- **接口路径**：`POST /api/v1/drafts/{draftId}/discard`
- **业务逻辑**：将草稿状态置为“已废弃”，不做物理删除。
- **响应参数** (Data):
  ```json
  {
    "draftId": 1001,
    "status": 2,
    "statusDesc": "已废弃"
  }
  ```

### 3.2 AI 创作模块 (AI Writing)

#### 1. 提交 AI 创作任务
- **接口路径**：`POST /api/v1/ai-writing/task/submit`
- **业务逻辑**：将大模型调用抽象为任务提交，支持后续的异步排队。
- **请求参数** (Body):
  ```json
  {
    "draftId": 1001,
    "taskType": "GENERATE_BODY", // GENERATE_OUTLINE, GENERATE_BODY, POLISH_TEXT, SUMMARIZE
    "promptParams": {
      "topic": "什么是信号量",
      "style": "通俗易懂，生活化例子"
    }
  }
  ```
- **响应参数** (Data):
  ```json
  {
    "taskId": 880234,
    "draftId": 1001,
    "taskType": "GENERATE_BODY",
    "status": 0,
    "statusDesc": "进行中"
  }
  ```

- **前端用途**：
  - 提交任务后立即拿到 `taskId`
  - 前端根据 `taskId` 建立 SSE 连接
  - 页面右侧 AI 面板切换到“生成中”状态

#### 2. 获取 AI 任务详情（建议补齐）
- **接口路径**：`GET /api/v1/ai-writing/task/{taskId}`
- **业务逻辑**：用于页面刷新后恢复任务状态，或在 SSE 连接中断后兜底查询。
- **响应参数** (Data):
  ```json
  {
    "taskId": 880234,
    "draftId": 1001,
    "taskType": "GENERATE_BODY",
    "status": 1,
    "statusDesc": "成功",
    "responseContent": "信号量（Semaphore）是一种用于控制并发访问共享资源的机制...",
    "errorMsg": null,
    "createTime": "2026-07-04 15:31:00",
    "updateTime": "2026-07-04 15:31:25"
  }
  ```

- **前端用途**：
  - 刷新编辑页后恢复最近一次任务结果
  - 若 SSE 断开，可用该接口确认任务最终状态
  - 页面重新进入时展示“上次 AI 生成结果”

#### 3. 流式获取生成结果 (SSE)
- **接口路径**：`GET /api/v1/ai-writing/task/stream?taskId=880234`
- **请求头**：`Accept: text/event-stream`
- **业务逻辑**：前端建立 SSE 连接，后端通过 `ResponseBodyEmitter` 或 `SseEmitter` 持续推送大模型生成的 token。
- **推荐事件格式**：尽量与当前前端 [agent.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/api/agent.ts) 中的 `StreamEvent` 风格保持一致。
- **返回事件流示例**：
  ```text
  data: {"phase": "thinking", "chunk": {"type": "status", "content": "正在分析需求..."}}
  
  data: {"phase": "generating", "chunk": {"type": "token", "content": "信号"}}
  data: {"phase": "generating", "chunk": {"type": "token", "content": "量是..."}}
  
  data: {"phase": "done", "chunk": {"type": "done", "content": ""}}
  ```

- **推荐前端消费规则**：
  - `type = status`：展示阶段提示
  - `type = token`：拼接到 `aiResultBuffer`
  - `type = error`：切换页面状态为 `error`
  - `type = done`：切换页面状态为 `done`

### 3.3 文章发布模块 (Publishing)

#### 1. 提交发布
- **接口路径**：`POST /api/v1/articles/publish`
- **业务逻辑**：校验草稿合法性，将草稿数据复制并落库至 `article` 和 `article_meta` 表，草稿状态置为“已发布”。
- **请求参数** (Body):
  ```json
  {
    "draftId": 1001,
    "tags": ["操作系统", "计算机基础"] // 最终确定的标签
  }
  ```
- **响应参数** (Data):
  ```json
  {
    "articleId": 5001,
    "draftId": 1001,
    "articleUrl": "/articles/5001",
    "publishTime": "2026-07-04 16:00:00"
  }
  ```

- **前端用途**：
  - 发布成功后立即跳转详情页
  - 同时在编辑页顶部更新“已发布”状态

#### 2. 获取社区文章列表 (分页)
- **接口路径**：`GET /api/v1/articles/page`
- **业务逻辑**：社区首页展示，关联查询 `article_meta` 统计数据。
- **请求参数** (Query):
  - `pageNo`, `pageSize`
- **响应参数** (Data):
  ```json
  {
    "total": 1024,
    "pageNo": 1,
    "pageSize": 10,
    "list": [
      {
        "articleId": 5001,
        "authorId": 20001,
        "authorName": "流云赴川",
        "avatarUrl": "...",
        "title": "计算机操作系统中的信号量",
        "summary": "简述信号量机制...",
        "coverUrl": "...",
        "publishTime": "2026-07-04 16:00:00",
        "viewCount": 342,
        "tags": ["操作系统"]
      }
    ]
  }
  ```

- **前端用途**：
  - `/articles` 文章列表页
  - `/` 工作台首页“最近发布文章”

#### 3. 获取文章详情
- **接口路径**：`GET /api/v1/articles/{articleId}`
- **响应参数** (Data):
  ```json
  {
    "articleId": 5001,
    "authorId": 20001,
    "authorName": "流云赴川",
    "avatarUrl": "https://...",
    "title": "计算机操作系统中的信号量",
    "summary": "简述信号量机制...",
    "contentMd": "## 什么是信号量...",
    "contentHtml": "<h2>什么是信号量</h2>...",
    "coverUrl": "https://...",
    "publishTime": "2026-07-04 16:00:00",
    "viewCount": 343,
    "tags": ["操作系统"]
  }
  ```

- **前端用途**：
  - `/articles/{articleId}` 详情页完整渲染
  - 如果前端后续采用 Markdown 渲染，使用 `contentMd`
  - 如果要服务端直出或减少渲染开销，可直接使用 `contentHtml`

### 3.4 建议补充的前端 TypeScript DTO

为了让前端实现更顺，建议后续在 [api.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/types/api.ts) 基础上补充以下 DTO：

```ts
export interface DraftDetailResponseDTO {
  draftId: number;
  userId: number;
  title: string;
  contentMd: string;
  summary?: string;
  coverUrl?: string;
  status: number;
  statusDesc: string;
  createTime: string;
  updateTime: string;
}

export interface DraftPageItemResponseDTO {
  draftId: number;
  title: string;
  summary?: string;
  coverUrl?: string;
  status: number;
  statusDesc: string;
  updateTime: string;
}

export interface AiTaskDetailResponseDTO {
  taskId: number;
  draftId: number;
  taskType: string;
  status: number;
  statusDesc: string;
  responseContent?: string;
  errorMsg?: string;
  createTime: string;
  updateTime: string;
}

export interface ArticleDetailResponseDTO {
  articleId: number;
  authorId: number;
  authorName: string;
  avatarUrl?: string;
  title: string;
  summary?: string;
  contentMd: string;
  contentHtml?: string;
  coverUrl?: string;
  publishTime: string;
  viewCount: number;
  tags: string[];
}
```

### 3.5 前后端联调清单（接口总览）

这一节不是新的设计，而是把上面的接口按“前端真正怎么调”重新整理成联调视角。

| 模块 | 接口 | 方法 | 前端页面 | 作用 |
| --- | --- | --- | --- | --- |
| Draft | `/api/v1/drafts/save` | `POST` | `/`、`/drafts/[draftId]` | 新建草稿、自动保存草稿 |
| Draft | `/api/v1/drafts/{draftId}` | `GET` | `/drafts/[draftId]` | 获取草稿完整详情 |
| Draft | `/api/v1/drafts/page` | `GET` | `/`、`/drafts`、`/me/drafts` | 获取草稿分页列表 |
| Draft | `/api/v1/drafts/{draftId}/discard` | `POST` | `/drafts`、`/drafts/[draftId]` | 废弃草稿 |
| AI Writing | `/api/v1/ai-writing/task/submit` | `POST` | `/drafts/[draftId]` | 发起 AI 写作任务 |
| AI Writing | `/api/v1/ai-writing/task/{taskId}` | `GET` | `/drafts/[draftId]` | 查询任务最终状态 |
| AI Writing | `/api/v1/ai-writing/task/stream` | `GET(SSE)` | `/drafts/[draftId]` | 流式接收 AI 结果 |
| Publishing | `/api/v1/articles/publish` | `POST` | `/drafts/[draftId]` | 发布草稿为文章 |
| Article | `/api/v1/articles/page` | `GET` | `/`、`/articles`、`/me/articles` | 获取文章分页列表 |
| Article | `/api/v1/articles/{articleId}` | `GET` | `/articles/[articleId]` | 获取文章详情 |

### 3.6 DTO 字段对表（联调用）

这一节的目标是：后端开发看字段要不要返回，前端开发看字段该怎么接。

#### 1. `SaveDraftRequestDTO`

| 字段 | 类型 | 必填 | 说明 | 前端来源 |
| --- | --- | --- | --- | --- |
| `draftId` | `number` | 否 | 有值表示更新，无值表示新建 | 路由参数或页面状态 |
| `title` | `string` | 否 | 草稿标题 | 标题输入框 |
| `contentMd` | `string` | 否 | Markdown 正文 | 编辑器正文 |
| `summary` | `string` | 否 | 草稿摘要 | 摘要输入区 |
| `coverUrl` | `string` | 否 | 封面地址 | 封面上传/输入框 |

请求示例：

```json
{
  "draftId": 1001,
  "title": "计算机操作系统中的信号量",
  "contentMd": "## 什么是信号量\n信号量（Semaphore）本质上是...",
  "summary": "简述信号量机制",
  "coverUrl": "https://example.com/cover.png"
}
```

#### 2. `SaveDraftResponseDTO`

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `draftId` | `number` | 草稿主键 | 新建后跳转编辑页 |
| `title` | `string` | 草稿标题 | 回填页面状态 |
| `status` | `number` | 草稿状态码 | 控制是否可继续编辑 |
| `lastUpdateTime` | `string` | 最后更新时间 | 展示“已保存”时间 |

#### 3. `DraftDetailResponseDTO`

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `draftId` | `number` | 草稿 ID | 页面主键 |
| `userId` | `number` | 作者 ID | 权限校验展示 |
| `title` | `string` | 标题 | 编辑器标题 |
| `contentMd` | `string` | 正文 Markdown | 编辑器内容 |
| `summary` | `string` | 摘要 | 发布前摘要编辑 |
| `coverUrl` | `string` | 封面图 | 头图展示 |
| `status` | `number` | 状态码 | 是否允许编辑/发布 |
| `statusDesc` | `string` | 状态描述 | 页面状态文案 |
| `createTime` | `string` | 创建时间 | 可选展示 |
| `updateTime` | `string` | 更新时间 | 顶部状态栏 |

#### 4. `DraftPageItemResponseDTO`

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `draftId` | `number` | 草稿 ID | 列表跳转 |
| `title` | `string` | 标题 | 卡片标题 |
| `summary` | `string` | 摘要 | 卡片摘要 |
| `coverUrl` | `string` | 封面 | 卡片缩略图 |
| `status` | `number` | 状态码 | 状态标签样式 |
| `statusDesc` | `string` | 状态描述 | 卡片状态文案 |
| `updateTime` | `string` | 更新时间 | 卡片时间 |

#### 5. `SubmitAiTaskRequestDTO`

| 字段 | 类型 | 必填 | 说明 | 前端来源 |
| --- | --- | --- | --- | --- |
| `draftId` | `number` | 是 | 关联草稿 ID | 当前编辑页 |
| `taskType` | `string` | 是 | 任务类型 | AI 按钮类型 |
| `promptParams` | `object` | 否 | 扩展参数 | 当前表单/用户选项 |

请求示例：

```json
{
  "draftId": 1001,
  "taskType": "POLISH_TEXT",
  "promptParams": {
    "selectedText": "信号量是一个整数变量。",
    "style": "更口语化，更适合博客表达"
  }
}
```

#### 6. `SubmitAiTaskResponseDTO`

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `taskId` | `number` | 任务 ID | 建立 SSE 连接 |
| `draftId` | `number` | 草稿 ID | 前端校验上下文 |
| `taskType` | `string` | 任务类型 | 右侧面板标题 |
| `status` | `number` | 状态码 | 切换任务状态 |
| `statusDesc` | `string` | 状态描述 | 展示“进行中” |

#### 7. `AiTaskDetailResponseDTO`

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `taskId` | `number` | 任务 ID | 页面恢复任务状态 |
| `draftId` | `number` | 草稿 ID | 校验归属 |
| `taskType` | `string` | 任务类型 | 展示任务类型 |
| `status` | `number` | 状态码 | done / failed 判断 |
| `statusDesc` | `string` | 状态描述 | 文案展示 |
| `responseContent` | `string` | 最终结果 | 回填 AI 结果区 |
| `errorMsg` | `string` | 失败原因 | 错误提示 |
| `createTime` | `string` | 创建时间 | 可选展示 |
| `updateTime` | `string` | 更新时间 | 可选展示 |

#### 8. `PublishArticleRequestDTO`

| 字段 | 类型 | 必填 | 说明 | 前端来源 |
| --- | --- | --- | --- | --- |
| `draftId` | `number` | 是 | 待发布草稿 | 当前编辑页 |
| `tags` | `string[]` | 否 | 文章标签 | 发布弹窗 |

请求示例：

```json
{
  "draftId": 1001,
  "tags": ["操作系统", "Java基础", "并发编程"]
}
```

#### 9. `PublishArticleResponseDTO`

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `articleId` | `number` | 文章 ID | 跳转详情页 |
| `draftId` | `number` | 来源草稿 ID | 页面状态同步 |
| `articleUrl` | `string` | 文章地址 | 直接路由跳转 |
| `publishTime` | `string` | 发布时间 | 发布提示 |

#### 10. `ArticlePageItemResponseDTO`

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `articleId` | `number` | 文章 ID | 列表跳转 |
| `authorId` | `number` | 作者 ID | 作者资料跳转 |
| `authorName` | `string` | 作者名 | 卡片作者信息 |
| `avatarUrl` | `string` | 头像 | 卡片头像 |
| `title` | `string` | 标题 | 卡片标题 |
| `summary` | `string` | 摘要 | 卡片摘要 |
| `coverUrl` | `string` | 封面 | 卡片缩略图 |
| `publishTime` | `string` | 发布时间 | 卡片时间 |
| `viewCount` | `number` | 阅读量 | 卡片统计 |
| `tags` | `string[]` | 标签 | 卡片标签 |

#### 11. `ArticleDetailResponseDTO`

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `articleId` | `number` | 文章 ID | 页面主键 |
| `authorId` | `number` | 作者 ID | 作者信息 |
| `authorName` | `string` | 作者名 | 作者信息 |
| `avatarUrl` | `string` | 作者头像 | 作者信息 |
| `title` | `string` | 标题 | 详情页标题 |
| `summary` | `string` | 摘要 | 文章导语 |
| `contentMd` | `string` | Markdown 正文 | Markdown 渲染 |
| `contentHtml` | `string` | HTML 正文 | 可选直出渲染 |
| `coverUrl` | `string` | 封面 | 头图 |
| `publishTime` | `string` | 发布时间 | 元信息栏 |
| `viewCount` | `number` | 阅读量 | 统计信息 |
| `tags` | `string[]` | 标签 | 标签区 |

### 3.7 前端请求示例（按页面场景）

这一节直接按页面写，方便联调时照着走。

#### 1. 工作台首页：获取最近草稿

```ts
const resp = await draftsApi.getDraftPage(1, 5);
const recentDrafts = resp.data.list;
```

#### 2. 草稿编辑页：初始化加载详情

```ts
const resp = await draftsApi.getDraftDetail(draftId);
setTitle(resp.data.title);
setContentMd(resp.data.contentMd);
setSummary(resp.data.summary ?? "");
setCoverUrl(resp.data.coverUrl ?? "");
```

#### 3. 草稿编辑页：自动保存

```ts
await draftsApi.saveDraft({
  draftId,
  title,
  contentMd,
  summary,
  coverUrl,
});
```

#### 4. 草稿编辑页：发起 AI 写作任务

```ts
const submitResp = await aiWritingApi.submitTask({
  draftId,
  taskType: "GENERATE_OUTLINE",
  promptParams: {
    topic: title,
    style: "技术博客风格"
  }
});

const taskId = submitResp.data.taskId;
```

#### 5. 草稿编辑页：监听 SSE 流

```ts
aiWritingApi.streamTask(
  taskId,
  (event) => {
    if (event.chunk.type === "token") {
      setAiResultBuffer((prev) => prev + event.chunk.content);
    }
  },
  (error) => {
    setAiTaskStatus("error");
  },
  () => {
    setAiTaskStatus("done");
  }
);
```

#### 6. 草稿编辑页：发布文章

```ts
const publishResp = await articlesApi.publishArticle({
  draftId,
  tags: ["操作系统", "并发"]
});

router.push(publishResp.data.articleUrl);
```

### 3.8 联调阶段的后端返回约束

为了减少前后端联调摩擦，建议后端在实现时尽量遵守以下约束：

1. **时间字段统一格式**
   - 统一返回 `yyyy-MM-dd HH:mm:ss`

2. **状态字段统一双返回**
   - 既返回 `status` 数值，也返回 `statusDesc` 文案

3. **列表字段不要缺省为 `null`**
   - `tags`、`list` 等建议返回空数组，而不是 `null`

4. **可选文本字段尽量返回空字符串或省略**
   - 避免前端大量判空分支

5. **发布成功后返回 `articleUrl`**
   - 前端可直接跳转，不再自行拼路径

## 4. 后端领域层（Domain）架构设计

> **重构背景**：
> 当前项目中的 `ChatService` 将会话、大模型调用和业务逻辑强耦合在一起，偏向于原型 Demo。
> 本期设计旨在利用 DDD（领域驱动设计）思想，将系统从“以聊天为中心”重构为“以内容生产和任务编排为中心”，提升系统的可扩展性和业务表达能力。

### 4.1 架构设计推演：收敛为 `content + agent` 双域结构

**现状判断（为什么不继续坚持三域拆分）**
当前项目的代码基础并不是一个从零开始设计的 DDD 工程，而是一个已经具备较强 AI 引擎能力的脚手架。在现有实现中，`agent` 包下已经同时存在：
1. `service/armory`：负责 Agent 注册、工作流节点编排、Runner 执行、MCP 能力装配。
2. `service/chat`：负责基于 `agentId` 获取注册信息、创建会话、驱动 Runner 发起调用。

这说明当前的 `agent` 已经不是单纯的“底层 SDK 适配层”，而是一个完整的 **AI 能力域**。如果此时再把 `ai_writing` 硬拆成一个与 `agent` 平级的独立领域，就会立刻遇到一个边界问题：`ai_writing` 是否可以直接调用 `IArmoryService` 或 `DefaultArmoryFactory`？从 DDD 边界上看，这会形成跨域直接服务依赖，概念上并不干净。

因此，结合当前项目阶段与已有代码资产，本期不再采用 `draft + article + ai_writing` 三个平级领域，而是收敛为两个核心领域：

1. **`content` 内容域**
   - 统一承载 `Draft`、`Article`、`ArticleMeta` 等内容资产。
   - `Draft` 与 `Article` 在数据库层继续物理分离，但在领域层归属同一个限界上下文，因为它们都围绕“内容生产与发布”这一组业务规则展开。
   - 这样既保留了数据层的隔离优势，也避免了顶层领域碎片化。

2. **`agent` AI 能力域**
   - 保留现有 `armory` 子模块，不动其自动装配、节点编排、Runner 组装等通用能力。
   - 将原有 `chat` 子模块升级为 **`ai_writing` 子模块**，语义从“聊天”扩展为“面向写作场景的 AI 能力封装”，例如生成大纲、正文续写、文本润色、摘要提炼。
   - 由于 `ai_writing` 与 `armory` 同属于 `agent` 域，因此它们之间的调用属于同域内部协作，是符合 DDD 语义的。

**本期结论**
第一期架构采用 **`content + agent` 双域结构**。其中：
- `content` 负责内容状态流转与发布规则；
- `agent` 负责 AI 能力编排；
- `ai_writing` 不是独立领域，而是 `agent` 域内部用于替代 `chat` 的业务子模块。

### 4.2 领域划分与包结构 (Package Structure)

在 `sutone-agent-bok-domain` 模块下，建议收敛为以下结构：

```text
cn.sutone.ai.domain
├── content                    // 1. 内容核心域
│   ├── model
│   │   ├── entity             // DraftEntity, ArticleEntity, ArticleMetaEntity
│   │   └── valobj             // DraftStatusEnum, ArticleStatusEnum 等
│   ├── repository             // IDraftRepository, IArticleRepository
│   └── service
│       ├── draft              // DraftDomainService
│       ├── article            // ArticleDomainService
│       └── publish            // PublishDomainService
└── agent                      // 2. AI 能力域
    ├── model
    │   ├── entity             // AiTaskEntity, ChatCommandEntity 等
    │   └── valobj             // Agent注册信息、配置值对象
    ├── repository             // IAiTaskRepository 等
    └── service
        ├── armory             // 保留原有 Agent 引擎、工作流、节点、自动装配
        └── ai_writing         // 由 chat 演进而来，封装写作类 AI 能力
```

**边界说明**
- `content` 域内部可以同时管理草稿与文章，但不意味着数据库表合并；领域归属统一，数据模型仍保持 `draft` / `article` 分离。
- `agent.ai_writing` 可以调用 `agent.armory`，因为二者属于同一限界上下文内部协作。
- 顶层领域之间仍保持克制，避免在 `content` 中直接堆砌大量 AI 执行细节。

### 4.3 核心领域服务设计 (Domain Service)

#### 1. `DraftDomainService`
- **职责**：负责草稿的创建、保存、查询、状态校验。
- **典型规则**：
  1. 自动保存时校验草稿归属用户。
  2. 仅允许“编辑中”状态的草稿继续被 AI 写入。
  3. 草稿被发布后，不再直接作为前台展示对象，而是成为文章快照的来源。

#### 2. `PublishDomainService`
- **职责**：负责 `Draft -> Article` 的发布转换。
- **业务流程**：
  1. 校验草稿状态与内容完整性（标题、正文不能为空）。
  2. 构建 `ArticleEntity` 与 `ArticleMetaEntity`。
  3. 调用内容域仓储完成事务保存。
  4. 回写草稿状态为“已发布”。

#### 3. `AiWritingDomainService`
- **职责**：封装写作相关的 AI 场景能力，而不是只处理单纯聊天。
- **典型能力**：
  1. 根据主题生成文章大纲。
  2. 根据草稿上下文续写正文。
  3. 对现有段落进行润色、总结、摘要提炼。
- **设计重点**：它面向的是“写作能力语义”，内部再调用 `armory` 去执行具体 Agent 工作流。

#### 4. `AiTaskDomainService`
- **职责**：管理 AI 任务生命周期。
- **业务流程**：
  1. 创建任务记录，保存请求上下文。
  2. 更新任务状态（`PENDING` / `RUNNING` / `SUCCESS` / `FAILED`）。
  3. 持久化最终生成结果或错误原因。

### 4.4 `agent` 域内子模块设计

本期 `agent` 域建议形成“引擎层 + 场景层”的内部结构：

1. **`armory` 子模块**
   - 保持原有职责，不承载博客内容业务语义。
   - 负责 Agent 注册、Runner 组装、工作流节点、MCP、插件、模型执行。
   - 可视为 `agent` 域内的通用执行内核。

2. **`ai_writing` 子模块**
   - 由现有 `chat` 重命名并扩展而来。
   - 对外不再只暴露“聊天”语义，而是暴露“写作能力”语义，例如 `generateOutline`、`generateBody`、`polishText`。
   - 内部通过 `DefaultArmoryFactory`、`IArmoryService` 或对应 Runner 获取执行能力，再结合 `AiTask` 完成写作流程。

这样的好处是：
- **兼容当前代码基础**：无需推翻现有 `armory` 自动装配体系。
- **边界更自洽**：避免独立 `ai_writing` 域对 `agent` 域形成跨域直接依赖。
- **演进更平滑**：后续如果 AI 场景继续增多，再考虑从 `agent` 域中继续抽象出更通用的 `ai_task` 或 `ai_capability` 上下文。

### 4.5 领域实体 (Entity) 建模示例

以 `ArticleEntity` 为例，内容域实体应采用充血模型，封装自己的状态变更行为：

```java
@Data
@Builder
public class ArticleEntity {
    private Long articleId;
    private Long draftId;
    private String title;
    private String contentMd;
    private ArticleStatusEnum status;

    private ArticleMetaEntity meta;

    public void offline() {
        if (this.status == ArticleStatusEnum.OFFLINE) {
            throw new DomainException("文章已经处于下线状态");
        }
        this.status = ArticleStatusEnum.OFFLINE;
    }
}
```

## 5. 前端页面交互与路由设计

> **设计目标**：
> 1. 延续当前前端项目已有的 Next.js App Router 结构，避免为了第一期方案推翻现有页面。
> 2. 以“登录 -> 工作台 -> AI 创作 -> 草稿编辑 -> 发布 -> 文章展示 -> 个人中心”作为主闭环。
> 3. 保持 Draw.io 与 PPT 页面可继续存在，但在第一期定位中降级为工作台内的创作能力入口，而非平台主叙事。
> 4. 前端方案尽量贴合当前后端 API 契约与现有前端实现，降低落地门槛。

### 5.1 当前前端现状与可复用页面

当前 `sutone-agent-bok-front` 已经存在如下 App Router 页面：

```text
src/app
├── page.tsx          // 工作台首页
├── login/page.tsx    // 登录页
├── drawio/page.tsx   // Draw.io 创作页
└── ppt/page.tsx      // PPT 创作页
```

现状上已经具备以下可复用能力：
- `login/page.tsx`：使用 Cookie 完成演示态登录拦截。
- `page.tsx`：已经具备“工作台首页”雏形，适合作为登录后的默认落点。
- `drawio/page.tsx`、`ppt/page.tsx`：都已经具备对话式创作工作区形态，可作为后续“AI 创作工作台”的交互参考模板。

因此，第一期前端设计不建议推倒重做，而是采用 **“保留现有页面 + 增量补齐内容创作页面”** 的策略。

### 5.2 第一期目标路由结构

围绕内容创作闭环，建议将前端路由收敛为以下结构：

```text
src/app
├── login/page.tsx                 // 登录页
├── page.tsx                       // 工作台首页 / 创作门户
├── studio/page.tsx                // AI 写作工作台首页（可选，若首页直接承接则可暂缓）
├── drafts
│   ├── page.tsx                   // 草稿箱列表页
│   └── [draftId]/page.tsx         // 草稿编辑页（第一期核心页）
├── articles
│   ├── page.tsx                   // 文章广场/列表页
│   └── [articleId]/page.tsx       // 文章详情页
├── me
│   ├── page.tsx                   // 个人中心
│   ├── drafts/page.tsx            // 我的草稿
│   └── articles/page.tsx          // 我的已发布文章
├── drawio/page.tsx                // 绘图工作区（保留）
└── ppt/page.tsx                   // PPT 工作区（保留）
```

### 5.3 页面定位与职责划分

#### 1. `/login`
- **页面定位**：平台登录入口。
- **当前实现**：使用 Cookie 存储演示登录态，已具备基础跳转能力。
- **第一期要求**：
  1. 登录成功后跳转到 `/`。
  2. 若已登录访问 `/login`，自动重定向到 `/`。
  3. 后续接入真实鉴权时，尽量保留现有“登录页只做输入与反馈”的页面职责，不在页面中堆积复杂业务逻辑。

#### 2. `/`
- **页面定位**：工作台首页，也是创作门户页。
- **页面职责**：
  1. 展示用户欢迎信息、最近草稿、最近发布文章、快捷创作入口。
  2. 将现有 Draw.io / PPT 卡片保留为“扩展创作工具”。
  3. 新增“AI 写文章”主入口，优先级高于 Draw.io / PPT。
- **交互目标**：用户登录后第一眼就能进入创作，而不是停留在工具列表页。
- **建议展示模块**：
  - 顶部：用户信息、退出登录、进入个人中心。
  - 中部：继续最近草稿、新建 AI 草稿、最近发布文章。
  - 底部：Draw.io、PPT 两个扩展工具入口。

#### 3. `/drafts`
- **页面定位**：草稿箱列表页。
- **页面职责**：
  1. 分页展示当前用户的全部草稿。
  2. 支持按更新时间排序。
  3. 支持继续编辑、删除/废弃、查看发布状态。
- **关键交互**：
  - 点击卡片进入 `/drafts/{draftId}`。
  - 点击“新建草稿”直接创建空草稿并跳转到编辑页。

#### 4. `/drafts/[draftId]`
- **页面定位**：第一期最核心的 AI 写作编辑页。
- **页面职责**：
  1. 左侧或中部为 Markdown 编辑器/预览区。
  2. 右侧为 AI 助手面板，承接“生成大纲、续写正文、润色、总结”等能力。
  3. 顶部展示草稿标题、保存状态、发布时间操作区。
- **关键交互**：
  - 自动保存草稿。
  - 提交 AI 任务后展示流式生成状态。
  - AI 结果可选择“插入正文”“替换选中段落”“追加到末尾”。
  - 发布成功后跳转文章详情页。

#### 5. `/articles`
- **页面定位**：文章广场/社区内容列表页。
- **页面职责**：
  1. 展示已发布文章列表。
  2. 支持分页、标签筛选（第一期可先做静态标签展示）。
  3. 展示摘要、作者、发布时间、阅读量等卡片信息。

#### 6. `/articles/[articleId]`
- **页面定位**：文章详情页。
- **页面职责**：
  1. 渲染正式发布文章内容。
  2. 展示作者信息、标签、发布时间、阅读量。
  3. 预留点赞、收藏、评论入口位置，但第一期不强制实现完整互动。

#### 7. `/me`、`/me/drafts`、`/me/articles`
- **页面定位**：个人中心与个人内容管理。
- **页面职责**：
  1. 展示用户基础资料、创作统计。
  2. 收口“我的草稿”和“我的文章”入口。
  3. 避免在首页重复堆叠过多管理功能。

### 5.4 前端目录与模块拆分建议

为了贴合当前 `sutone-agent-bok-front` 的 Next.js App Router 结构，建议按“页面、组件、API、类型、工具”拆分：

```text
src
├── app
│   ├── login/page.tsx
│   ├── page.tsx
│   ├── drafts
│   │   ├── page.tsx
│   │   └── [draftId]/page.tsx
│   ├── articles
│   │   ├── page.tsx
│   │   └── [articleId]/page.tsx
│   ├── me/page.tsx
│   ├── drawio/page.tsx
│   └── ppt/page.tsx
├── components
│   ├── layout                 // 顶栏、侧边栏、页面容器
│   ├── draft                  // 编辑器、工具栏、AI 面板、草稿卡片
│   ├── article                // 文章卡片、详情页头部、标签区
│   ├── common                 // EmptyState、Loading、Pagination、Modal
│   └── auth                   // 登录表单、用户信息栏
├── api
│   ├── auth.ts                // 登录、当前用户信息（后续可扩展）
│   ├── drafts.ts              // 草稿接口
│   ├── articles.ts            // 文章接口
│   ├── ai-writing.ts          // AI 写作任务与 SSE
│   └── agent.ts               // 保留当前 drawio/ppt/agent 能力
├── types
│   ├── api.ts
│   ├── draft.ts
│   ├── article.ts
│   └── ai-writing.ts
└── utils
    ├── cookie.ts
    ├── auth.ts
    └── markdown.ts
```

**这样拆的意义**：
- `app` 只负责页面入口，不把所有 JSX 和请求逻辑全塞在 `page.tsx` 里。
- `api` 层一一对应后端资源接口，便于你从 API 文档直接落到前端代码。
- `components/draft` 和 `components/article` 可以承接后续复杂页面，避免单文件过大。

### 5.5 页面与后端 API 的映射关系

这一节是为了让前端方案尽量贴着后端设计，不再停留在“页面概念”层。

#### 1. 登录页 `/login`
第一期前端可以先延续当前 Cookie 演示登录逻辑，后续若后端提供登录接口，再将其替换为真实请求。

**当前落地方式**：
- 使用 [cookie.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/utils/cookie.ts) 读写用户登录态。
- 登录成功后跳转 `/`。

#### 2. 工作台首页 `/`
建议调用以下后端接口组合出首页数据：
- `GET /api/v1/drafts/page`：取最近草稿
- `GET /api/v1/articles/page`：取最近发布文章

**页面展示与接口关系**：
- “继续最近草稿”模块：取草稿列表前 3-5 条
- “最近发布文章”模块：取文章列表前 3-5 条
- “新建 AI 草稿”按钮：先调 `POST /api/v1/drafts/save` 创建空草稿，再跳转编辑页

#### 3. 草稿箱页 `/drafts`
- 对应接口：`GET /api/v1/drafts/page`
- 页面状态：
  - `loading`：首次进入页面
  - `empty`：用户还没有草稿
  - `success`：分页展示草稿卡片
  - `error`：接口失败，展示重试按钮

建议草稿卡片字段直接对应后端返回：
- `draftId`
- `title`
- `summary`
- `updateTime`

#### 4. 草稿编辑页 `/drafts/[draftId]`
这是最重要的一页，对应后端接口最多。

**建议至少对接这些接口**：
- `POST /api/v1/drafts/save`：保存草稿
- `POST /api/v1/ai-writing/task/submit`：提交 AI 任务
- `GET /api/v1/ai-writing/task/stream?taskId=xxx`：监听 AI 流式结果
- `POST /api/v1/articles/publish`：发布文章

**前端要维护的核心状态**：
- `draftId`
- `title`
- `contentMd`
- `summary`
- `coverUrl`
- `saveStatus`：`idle / saving / saved / error`
- `aiTaskStatus`：`idle / pending / streaming / done / error`
- `aiResultBuffer`：流式拼接的结果文本

**页面动作与接口对应关系**：
1. 用户修改标题或正文
   - 本地立即更新编辑器状态
   - 延迟 800ms ~ 1500ms 防抖后调用 `POST /api/v1/drafts/save`

2. 用户点击“生成大纲/续写/润色”
   - 调用 `POST /api/v1/ai-writing/task/submit`
   - 拿到 `taskId`
   - 基于 `taskId` 建立 SSE 连接
   - 将 SSE 返回的 token 逐步拼接到 `aiResultBuffer`

3. 用户点击“插入正文”
   - 把 `aiResultBuffer` 合并进 `contentMd`
   - 再触发一次保存接口

4. 用户点击“发布文章”
   - 调用 `POST /api/v1/articles/publish`
   - 成功后拿到 `articleId`
   - 跳转 `/articles/{articleId}`

#### 5. 文章列表页 `/articles`
- 对应接口：`GET /api/v1/articles/page`
- 展示字段与后端返回对齐：
  - `articleId`
  - `authorName`
  - `avatarUrl`
  - `title`
  - `summary`
  - `coverUrl`
  - `publishTime`
  - `viewCount`
  - `tags`

#### 6. 文章详情页 `/articles/[articleId]`
- 对应接口：`GET /api/v1/articles/{articleId}`
- 页面字段建议直接映射后端：
  - 标题、作者、发布时间、标签
  - Markdown 正文
  - 阅读量

### 5.6 推荐的用户主流程

第一期前端主流程建议固定为：

```text
/login
  -> /
  -> /drafts 或 /drafts/{draftId}
  -> 发起 AI 创作任务
  -> 自动保存草稿
  -> 发布
  -> /articles/{articleId}
  -> /me/articles
```

拆解成页面动作如下：

1. **登录**
   - 用户在 `/login` 输入账号密码。
   - 登录成功后写入 Cookie 并跳转首页。

2. **进入工作台**
   - 首页展示“继续最近草稿”“新建 AI 草稿”“进入 Draw.io / PPT”。
   - 创作主链路默认引导到草稿编辑，而不是分散到工具页。

3. **进入草稿编辑**
   - 若是新建，前端先调用保存接口创建空草稿，再跳转 `/drafts/{draftId}`。
   - 若是继续编辑，则从草稿箱或首页最近草稿进入。

4. **调用 AI 创作**
   - 用户在编辑页点击“生成大纲”“续写正文”等按钮。
   - 页面右侧 AI 面板展示任务状态、生成片段、失败提示。
   - 生成结果经用户确认后写入编辑器内容区。

5. **发布与跳转**
   - 用户点击“发布文章”。
   - 发布成功后跳转到 `/articles/{articleId}`，形成从草稿态到展示态的页面闭环。

### 5.7 草稿编辑页的详细交互设计（第一期重点）

`/drafts/[draftId]` 建议采用三段式布局：

1. **顶部操作栏**
   - 返回草稿箱
   - 草稿标题输入
   - 保存状态（保存中 / 已保存 / 保存失败）
   - 发布按钮

2. **中间主编辑区**
   - Markdown 编辑器
   - 支持标题、正文、摘要、封面基础信息
   - 可切换编辑/预览模式

3. **右侧 AI 辅助面板**
   - 快捷指令：生成大纲、续写、润色、总结
   - 任务执行状态：等待中、生成中、完成、失败
   - 结果操作按钮：插入、替换、追加、重试

这样设计的原因是：**AI 不应抢占主编辑器，而应作为编辑过程中的增强面板存在**。用户始终是在“写草稿”，而不是在“聊天窗口里碰运气”。

进一步细化到交互层，建议如下：

#### 1. 页面初始化
- 从路由参数获取 `draftId`
- 页面挂载后请求草稿详情
- 若草稿不存在或无权限，跳转错误提示页或返回草稿箱

> 注：第 3 节现已补充 `GET /api/v1/drafts/{draftId}`，编辑页初始化时应优先使用该接口拉取完整草稿内容，而不是依赖列表页透传数据。

#### 2. 自动保存策略
- 用户输入后不立即每次请求后端
- 采用防抖策略：停止输入 1 秒左右触发保存
- 保存中显示 `保存中...`
- 保存成功显示 `已保存`
- 保存失败显示红色错误提示，并允许手动重试

#### 3. AI 任务发起策略
- 点击“生成大纲”后，右侧 AI 面板进入 `pending`
- 接口返回 `taskId` 后进入 `streaming`
- SSE 数据按 token 或 chunk 实时渲染到结果区
- 结束后进入 `done`
- 若中途失败，进入 `error` 并保留“重试”按钮

#### 4. AI 结果落稿策略
建议前端不要让 AI 结果自动覆盖正文，而是必须让用户主动选择：
- `插入到光标处`
- `替换当前选中内容`
- `追加到文末`

这样更符合内容创作场景，也能减少误操作。

### 5.8 草稿编辑页推荐组件拆分

为了避免 `/drafts/[draftId]/page.tsx` 变成一个超大文件，建议拆成以下组件：

```text
components/draft
├── draft-editor-page.tsx        // 页面组合层
├── draft-toolbar.tsx            // 顶部工具栏
├── draft-title-input.tsx        // 标题输入
├── markdown-editor.tsx          // Markdown 编辑器
├── markdown-preview.tsx         // Markdown 预览区
├── ai-writing-panel.tsx         // AI 助手侧边栏
├── ai-task-status.tsx           // 任务状态区
├── ai-result-actions.tsx        // 插入/替换/追加按钮区
└── publish-dialog.tsx           // 发布确认弹窗
```

各组件职责建议如下：

1. `draft-editor-page.tsx`
   - 负责组织页面状态
   - 调用草稿保存、AI 任务提交、发布接口

2. `draft-toolbar.tsx`
   - 返回按钮
   - 保存状态
   - 发布按钮

3. `markdown-editor.tsx`
   - 只负责文本输入
   - 不承担业务请求逻辑

4. `ai-writing-panel.tsx`
   - 展示 AI 指令按钮
   - 展示流式结果
   - 展示错误与重试

### 5.9 登录态与路由守卫设计

1. **公开页**
   - `/login`
   - `/articles`
   - `/articles/[articleId]`

2. **登录后可访问页**
   - `/`
   - `/drafts`
   - `/drafts/[draftId]`
   - `/me`
   - `/drawio`
   - `/ppt`

3. **路由策略**
   - 未登录访问受保护页面时，跳转到 `/login`。
   - 已登录访问 `/login` 时，跳转到 `/`。
   - 后续若接入真实后端鉴权，可将当前 Cookie 方案替换为 Token + 中间件校验，但前端路由结构无需大改。

**贴合当前项目的建议做法**：
- 继续保留现有 [cookie.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/utils/cookie.ts) 作为一期登录态方案。
- 可以新增 `utils/auth.ts`，统一封装：
  - `requireLogin(router)`
  - `getCurrentUser()`
  - `logout(router)`

这样可以避免每个页面都重复写 `useEffect + getUserInfo + router.push('/login')`。

### 5.10 与当前页面的演进关系

为了避免第一期改动过大，建议分两步演进：

1. **第一步：保留现有页面，新增内容创作链路**
   - 保留 `/login`、`/`、`/drawio`、`/ppt`。
   - 新增 `/drafts`、`/drafts/[draftId]`、`/articles/[articleId]` 等核心页面。
   - 将首页中的主按钮从“工具入口”调整为“创作入口优先”。

2. **第二步：再考虑首页与创作台合并**
   - 若后续首页与创作台职责越来越接近，可以将 `/` 逐渐演进为真正的创作中台首页。
   - Draw.io 与 PPT 则作为工作台里的二级工具能力长期保留。

### 5.11 前端状态管理建议

第一期不建议一开始就引入过重的全局状态管理，建议采用以下分层：

1. **页面本地状态**
   - 表单输入、面板开关、流式状态、加载态等，优先使用 React `useState`。

2. **会话级持久化状态**
   - 当前用户信息：Cookie。
   - 创作会话草稿临时缓存：可先复用 `localStorage` 思路，作为编辑器恢复兜底。

3. **服务端状态**
   - 草稿详情、草稿列表、文章详情、发布结果、AI 任务状态等，统一视为服务端状态，后续可考虑引入 `SWR` 或 `React Query` 收口。

### 5.12 前端请求层设计建议

当前前端已经有 [agent.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/api/agent.ts) 和 [api.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/types/api.ts) 作为调用 Agent 能力的基础。为了贴合新的后端内容平台接口，建议新增以下 API 模块：

#### 1. `src/api/drafts.ts`

建议封装：

```ts
saveDraft(data)
getDraftPage(pageNo, pageSize)
getDraftDetail(draftId)
```

#### 2. `src/api/ai-writing.ts`

建议封装：

```ts
submitAiTask(data)
streamAiTask(taskId, onMessage, onError, onDone)
```

这一层可以直接复用当前 [agent.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/api/agent.ts) 中已有的流式处理思路，只是把路径从 `/chat_stream` 换成新的 `/ai-writing/task/stream`。

#### 3. `src/api/articles.ts`

建议封装：

```ts
publishArticle(data)
getArticlePage(pageNo, pageSize)
getArticleDetail(articleId)
```

#### 4. 公共返回结构

可以继续沿用当前：

```ts
export interface Response<T> {
  code: string;
  info: string;
  data: T;
}
```

这样能保证 Draw.io / PPT / 内容平台三类接口风格统一。

### 5.13 第一期开页优先级

建议按以下顺序落地前端页面：

1. `/login`（已存在，轻改即可）
2. `/` 工作台首页（已存在，需要调整主入口表达）
3. `/drafts/[draftId]` 草稿编辑页（第一优先级）
4. `/drafts` 草稿箱页
5. `/articles/[articleId]` 文章详情页
6. `/articles` 文章列表页
7. `/me` 个人中心页

也就是说，**前端第一期真正的核心不在 Draw.io 和 PPT，而在草稿编辑页与文章详情页。**

### 5.14 前端实现注意点（给后端开发者的提醒）

考虑到当前项目主要由后端视角驱动，这里补充一些实现时容易踩坑的点：

1. **不要一开始就上复杂状态管理**
   - 草稿编辑页使用 `useState + useEffect + 防抖` 已经足够。

2. **不要让页面直接充满 fetch**
   - 所有请求尽量收口到 `src/api`。

3. **不要让 AI 结果直接覆盖正文**
   - 一定要给用户确认动作。

4. **优先保证草稿编辑页稳定**
   - 第一阶段不是追求页面多，而是追求“编辑、保存、生成、发布”这条链顺。

5. **先以 Client Component 为主**
   - 由于当前项目大量依赖 Cookie、浏览器交互、SSE、富文本编辑，第一期可优先使用客户端组件实现。
   - 后续文章详情页、文章列表页再考虑更多 Server Component 优化。

## 6. AI 任务与流式交互（SSE）设计

> **设计目标**：
> 1. 解决大模型生成耗时长、普通 HTTP 请求容易超时的问题。
> 2. 让前端在草稿编辑页中实时看到生成过程，而不是一直等待最终结果。
> 3. 将一次 AI 生成沉淀为可追踪、可恢复、可重试的 `AiTask` 业务资产。
> 4. 尽量兼容当前前端 [agent.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/api/agent.ts) 中已经存在的流式解析模式。

### 6.1 为什么这里必须使用“两步式任务 + 流式返回”

AI 写作类请求与普通 CRUD 最大的区别在于：

1. **耗时不可控**
   - 生成大纲可能几秒钟，生成长正文可能数十秒。
   - 如果前端直接 `POST` 一个同步请求等待完整结果，用户体验会非常差，也容易被网关超时打断。

2. **结果天然适合增量展示**
   - 对于“生成大纲”“续写正文”“润色”这类能力，用户并不需要等到全部结束才看到内容。
   - 逐 token / 逐 chunk 推送更接近真实创作过程。

3. **生成过程本身也是业务资产**
   - 前端可能刷新页面
   - SSE 可能中断
   - 用户可能想查看上一次生成结果
   - 后端也需要记录失败原因与最终结果

因此，一期建议固定采用如下模式：

```text
1. POST /api/v1/ai-writing/task/submit
   -> 创建 AiTask
   -> 返回 taskId

2. GET /api/v1/ai-writing/task/stream?taskId=xxx
   -> 持续推送状态/内容事件

3. GET /api/v1/ai-writing/task/{taskId}
   -> 页面刷新或流中断时兜底恢复
```

这比“一次请求直接返回最终全文”更适合 AI 创作场景。

### 6.2 总体交互时序

从前端编辑页视角，推荐时序如下：

```text
用户点击“生成大纲”
  -> 前端调用 submitTask
  -> 后端创建 AiTask(status=PENDING/RUNNING)
  -> 返回 taskId
  -> 前端进入 pending / streaming 状态
  -> 前端基于 taskId 建立流式连接
  -> 后端持续输出 status/token 事件
  -> 前端实时拼接 aiResultBuffer
  -> 后端输出 done 或 error 事件
  -> 前端结束流式状态
  -> 用户选择“插入正文 / 替换 / 追加”
  -> 前端调用 saveDraft 落稿
```

### 6.3 推荐事件协议

为了兼容当前前端的实现方式，一期建议采用：

- **响应媒体类型**：`text/event-stream`
- **传输内容格式**：逐行 JSON 事件
- **前端消费方式**：使用 `fetch + ReadableStream` 逐行读取并解析

这意味着：虽然协议语义上是 SSE，但前端不强依赖 `EventSource`，而是沿用当前项目里更灵活的流式读取方式。

#### 推荐事件对象

```ts
export interface StreamEvent {
  phase: 'thinking' | 'generating' | 'done' | 'error';
  chunk: {
    type: 'status' | 'token' | 'done' | 'error';
    content: string;
  };
}
```

#### 推荐事件示例

```text
data: {"phase":"thinking","chunk":{"type":"status","content":"正在分析草稿上下文..."}}

data: {"phase":"thinking","chunk":{"type":"status","content":"正在构建写作提示词..."}}

data: {"phase":"generating","chunk":{"type":"token","content":"信号量"}}
data: {"phase":"generating","chunk":{"type":"token","content":"（Semaphore）"}}
data: {"phase":"generating","chunk":{"type":"token","content":"是一种用于..."}}

data: {"phase":"done","chunk":{"type":"done","content":""}}
```

异常时：

```text
data: {"phase":"error","chunk":{"type":"error","content":"模型服务超时，请稍后重试"}}
```

### 6.4 事件类型说明

#### 1. `status`
- **作用**：展示阶段提示，不直接写入正文。
- **适合内容**：
  - 正在分析需求
  - 正在读取草稿上下文
  - 正在组装 Prompt
  - 正在调用模型

#### 2. `token`
- **作用**：真正的正文片段。
- **前端处理**：拼接到 `aiResultBuffer`，实时展示在 AI 面板中。

#### 3. `done`
- **作用**：通知前端本次流结束。
- **前端处理**：
  - 停止 loading / streaming 状态
  - 显示“插入正文 / 替换 / 追加”操作按钮

#### 4. `error`
- **作用**：通知前端生成失败。
- **前端处理**：
  - 显示错误提示
  - 保留“重试”按钮
  - 不自动清空已生成内容，便于用户决定是否保留

### 6.5 前端状态机设计

编辑页的 AI 面板建议至少维护如下状态：

```ts
type AiTaskStatus =
  | 'idle'
  | 'pending'
  | 'streaming'
  | 'done'
  | 'error';
```

推荐状态流转：

```text
idle
  -> pending      // submitTask 已提交，等待开始流
  -> streaming    // 收到流式内容
  -> done         // 收到 done 事件
  -> error        // 收到 error 事件或网络异常
```

对应前端表现：

| 状态 | UI 表现 |
| --- | --- |
| `idle` | 展示 AI 指令按钮 |
| `pending` | 按钮禁用，提示“任务提交中 / 等待生成” |
| `streaming` | 实时显示 token，展示停止按钮 |
| `done` | 展示结果操作按钮 |
| `error` | 展示错误提示与重试按钮 |

### 6.6 前端消费逻辑建议

基于当前 [agent.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/api/agent.ts) 的实现风格，`src/api/ai-writing.ts` 可以沿用相同思路：

```ts
streamTask(taskId, onEvent, onError, onComplete)
```

前端核心逻辑建议：

1. 调用 `submitTask`
   - 拿到 `taskId`
   - `setAiTaskStatus('pending')`

2. 调用 `streamTask(taskId, ...)`
   - 收到 `status`：更新阶段提示文案
   - 收到 `token`：`setAiResultBuffer(prev => prev + token)`
   - 收到 `done`：`setAiTaskStatus('done')`
   - 收到 `error`：`setAiTaskStatus('error')`

3. 用户确认落稿
   - 合并 `aiResultBuffer` 到 `contentMd`
   - 再触发 `saveDraft`

### 6.7 页面刷新与中断恢复

SSE 在浏览器环境里天然可能遇到：

- 用户刷新页面
- 路由切换
- 网络抖动
- 用户主动中止生成

因此一期必须有恢复策略：

#### 1. 刷新页面后的恢复
- 编辑页初始化时，如果本地仍记录最近一次 `taskId`
- 调用 `GET /api/v1/ai-writing/task/{taskId}`
- 根据返回状态恢复页面：
  - `RUNNING`：提示任务仍在执行，可重新连接流
  - `SUCCESS`：回填 `responseContent`
  - `FAILED`：展示失败原因

#### 2. 用户主动停止
- 前端调用 `AbortController.abort()`
- 前端 UI 状态退出 `streaming`
- 一期可不强制后端提供“取消任务”接口，但后续建议补充

#### 3. SSE 中断
- 前端提示“连接中断，可刷新任务状态”
- 通过 `GET /task/{taskId}` 进行兜底

### 6.8 后端任务状态设计

结合前面数据库设计，一期建议 `ai_task.status` 至少支持以下状态：

| 状态值 | 状态名 | 说明 |
| --- | --- | --- |
| `0` | `RUNNING` | 已提交，正在处理 |
| `1` | `SUCCESS` | 已完成 |
| `2` | `FAILED` | 失败 |

如果你希望更细一点，也可以扩展为：

| 状态值 | 状态名 | 说明 |
| --- | --- | --- |
| `0` | `PENDING` | 已创建，尚未真正开始生成 |
| `1` | `RUNNING` | 正在生成 |
| `2` | `SUCCESS` | 已完成 |
| `3` | `FAILED` | 失败 |
| `4` | `CANCELLED` | 用户主动取消 |

一期为了实现简单，可以先采用三态；如果你后面要做更完整的任务中心，再扩成五态更自然。

### 6.9 后端流式实现建议

后端在 Spring Boot 中可采用：

- `SseEmitter`
- 或 `ResponseBodyEmitter`

建议职责分工如下：

1. **提交任务接口**
   - 创建 `AiTask`
   - 保存 `promptPayload`
   - 返回 `taskId`

2. **流式接口**
   - 校验 `taskId`
   - 根据任务上下文调用 `agent.ai_writing`
   - 持续输出 `status/token/done/error`

3. **任务完成后**
   - 把完整结果更新到 `responseContent`
   - 成功则写 `SUCCESS`
   - 失败则写 `FAILED + errorMsg`

### 6.10 与当前 `agent` 域的衔接方式

由于当前项目已经有 [agent.ts](file:///d:/java/scaffold/Ai-agent-bok/sutone-agent-bok-front/src/api/agent.ts) 对应的流式模式，以及后端 `agent` 域中已有 `chat` / `armory` 相关执行逻辑，因此一期建议：

1. 保持底层 `armory` 继续负责模型执行
2. 将 `chat` 演进为 `ai_writing`
3. 在 `ai_writing` 中把底层模型事件统一包装成内容平台自己的 `StreamEvent`

也就是说，前端不要直接消费底层模型 SDK 的原始事件，而是消费平台统一格式的事件。

这样做的收益是：
- 前端事件协议稳定
- 后端底层模型可替换
- Draw.io / PPT / 内容写作都可以复用同一种流式包装思想

### 6.11 联调检查清单

前后端联调 SSE 时，建议逐项确认：

1. `submitTask` 是否能稳定返回 `taskId`
2. 流式接口是否能连续输出多条事件，而不是最后一次性返回
3. 前端是否能正确拼接 `token`
4. `done` 事件是否一定发送
5. 出错时是否一定发送 `error`
6. `ai_task` 表中的 `responseContent`、`status`、`errorMsg` 是否落库
7. 页面刷新后是否能通过 `GET /task/{taskId}` 恢复状态

### 6.12 一期实现边界

为了控制复杂度，一期的 SSE 设计边界建议如下：

**一期必须做**
- 提交任务
- 流式展示 token
- 任务状态落库
- 失败提示
- 页面刷新后的任务结果恢复

**一期可以暂缓**
- 多任务并发队列
- 用户主动取消任务并中断后端生成
- token 消耗统计
- 任务重试中心
- 流式断点续传

这样可以保证第一期把“AI 创作闭环”做通，而不是被任务平台化细节拖慢。

## 7. 后端内容平台骨架设计清单

> **目标**：
> 将前面已经确认的 API、DTO、双域结构和 SSE 方案，收敛成一份可执行的后端落地清单。

### 7.1 模块职责总览

结合当前工程结构，后端内容平台建议仍然落在现有四层模块中：

| 模块 | 作用 | 本期新增内容 |
| --- | --- | --- |
| `sutone-agent-bok-api` | 对外接口定义、DTO、统一返回体 | 新增内容平台 API 接口与请求/响应 DTO |
| `sutone-agent-bok-trigger` | HTTP Controller 层 | 新增草稿、AI 写作、文章发布相关 Controller |
| `sutone-agent-bok-domain` | 领域模型与领域服务 | 新增 `content` 域与 `agent.ai_writing` 子模块骨架 |
| `sutone-agent-bok-infrastructure` | 仓储实现、DAO、外部适配 | 新增内容表 DAO、Repository 实现、SSE 适配支撑 |

### 7.2 API 模块骨架清单（`sutone-agent-bok-api`）

建议新增包结构：

```text
cn.sutone.ai.api
├── IContentDraftService.java
├── IAiWritingService.java
├── IArticleService.java
└── dto
    ├── draft
    ├── aiwriting
    └── article
```

#### 1. 草稿接口

建议新增：

```text
cn.sutone.ai.api.IContentDraftService
```

建议定义的方法：

```java
Response<SaveDraftResponseDTO> saveDraft(SaveDraftRequestDTO requestDTO);
Response<DraftDetailResponseDTO> queryDraftDetail(Long draftId);
Response<PageResponse<DraftPageItemResponseDTO>> queryDraftPage(Integer pageNo, Integer pageSize);
Response<DiscardDraftResponseDTO> discardDraft(Long draftId);
```

#### 2. AI 写作接口

建议新增：

```text
cn.sutone.ai.api.IAiWritingService
```

建议定义的方法：

```java
Response<SubmitAiTaskResponseDTO> submitTask(SubmitAiTaskRequestDTO requestDTO);
Response<AiTaskDetailResponseDTO> queryTaskDetail(Long taskId);
```

流式接口由于返回类型特殊，可以由 Trigger 层直接实现，不强制写入 API 接口模块。

#### 3. 文章接口

建议新增：

```text
cn.sutone.ai.api.IArticleService
```

建议定义的方法：

```java
Response<PublishArticleResponseDTO> publishArticle(PublishArticleRequestDTO requestDTO);
Response<PageResponse<ArticlePageItemResponseDTO>> queryArticlePage(Integer pageNo, Integer pageSize);
Response<ArticleDetailResponseDTO> queryArticleDetail(Long articleId);
```

### 7.3 API DTO 骨架清单

建议在 `sutone-agent-bok-api/src/main/java/cn/sutone/ai/api/dto` 下新增三个子包：

```text
dto
├── draft
├── aiwriting
└── article
```

#### draft 包

建议新增：

```text
SaveDraftRequestDTO.java
SaveDraftResponseDTO.java
DraftDetailResponseDTO.java
DraftPageItemResponseDTO.java
DiscardDraftResponseDTO.java
```

#### aiwriting 包

建议新增：

```text
SubmitAiTaskRequestDTO.java
SubmitAiTaskResponseDTO.java
AiTaskDetailResponseDTO.java
AiWritingStreamEventDTO.java
AiWritingChunkDTO.java
```

#### article 包

建议新增：

```text
PublishArticleRequestDTO.java
PublishArticleResponseDTO.java
ArticlePageItemResponseDTO.java
ArticleDetailResponseDTO.java
```

#### 通用分页 DTO

当前 API 模块里还没有分页返回对象，建议补一个：

```text
cn.sutone.ai.api.dto.PageResponseDTO<T>
```

字段建议：

```java
private Integer total;
private Integer pageNo;
private Integer pageSize;
private List<T> list;
```

### 7.4 Trigger 层 Controller 骨架清单（`sutone-agent-bok-trigger`）

建议新增包结构：

```text
cn.sutone.ai.trigger.http
├── ContentDraftController.java
├── AiWritingController.java
└── ArticleController.java
```

#### 1. `ContentDraftController`

负责接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/drafts/save` | 保存/更新草稿 |
| `GET` | `/api/v1/drafts/{draftId}` | 查询草稿详情 |
| `GET` | `/api/v1/drafts/page` | 查询草稿分页 |
| `POST` | `/api/v1/drafts/{draftId}/discard` | 废弃草稿 |

#### 2. `AiWritingController`

负责接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/ai-writing/task/submit` | 提交 AI 任务 |
| `GET` | `/api/v1/ai-writing/task/{taskId}` | 查询任务详情 |
| `GET` | `/api/v1/ai-writing/task/stream` | SSE 流式返回 |

#### 3. `ArticleController`

负责接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/articles/publish` | 发布文章 |
| `GET` | `/api/v1/articles/page` | 查询文章分页 |
| `GET` | `/api/v1/articles/{articleId}` | 查询文章详情 |

### 7.5 Domain 层骨架清单（`sutone-agent-bok-domain`）

结合前文确定的双域结构，本期建议新增如下包：

```text
cn.sutone.ai.domain
├── content
│   ├── model
│   │   ├── entity
│   │   └── valobj
│   ├── repository
│   └── service
│       ├── draft
│       ├── article
│       └── publish
└── agent
    ├── model
    │   ├── entity
    │   └── valobj
    ├── repository
    └── service
        ├── armory
        └── ai_writing
```

#### 1. `content` 域建议新增的实体

```text
DraftEntity.java
ArticleEntity.java
ArticleMetaEntity.java
```

#### 2. `content` 域建议新增的值对象 / 枚举

```text
DraftStatusEnum.java
ArticleStatusEnum.java
```

#### 3. `content` 域建议新增的仓储接口

```text
IDraftRepository.java
IArticleRepository.java
```

#### 4. `content` 域建议新增的服务

```text
draft/DraftDomainService.java
article/ArticleDomainService.java
publish/PublishDomainService.java
```

职责建议：

| 类名 | 职责 |
| --- | --- |
| `DraftDomainService` | 保存草稿、查询草稿、校验草稿状态 |
| `ArticleDomainService` | 查询文章列表、文章详情、阅读量处理 |
| `PublishDomainService` | 执行 `Draft -> Article` 发布转换 |

#### 5. `agent.ai_writing` 建议新增的实体 / 值对象

```text
AiTaskEntity.java
PromptContextEntity.java
AiTaskStatusEnum.java
AiWritingTaskTypeEnum.java
```

#### 6. `agent.ai_writing` 建议新增的仓储接口

```text
IAiTaskRepository.java
```

#### 7. `agent.ai_writing` 建议新增的服务

```text
AiWritingDomainService.java
AiTaskDomainService.java
StreamGenerationDomainService.java
```

职责建议：

| 类名 | 职责 |
| --- | --- |
| `AiWritingDomainService` | 组织写作场景能力，如生成大纲、正文续写、润色 |
| `AiTaskDomainService` | 创建任务、更新任务状态、保存结果 |
| `StreamGenerationDomainService` | 将底层生成事件包装为平台统一 SSE 事件 |

### 7.6 Infrastructure 层骨架清单（`sutone-agent-bok-infrastructure`）

建议新增三类内容：

#### 1. DAO / PO

```text
dao
├── IDraftDao.java
├── IArticleDao.java
├── IArticleMetaDao.java
├── IAiTaskDao.java
└── po
    ├── DraftPO.java
    ├── ArticlePO.java
    ├── ArticleMetaPO.java
    └── AiTaskPO.java
```

#### 2. Repository 实现

```text
adapter/repository
├── DraftRepository.java
├── ArticleRepository.java
└── AiTaskRepository.java
```

#### 3. 外部能力适配

当前 `agent` 域已有底层 AI 执行能力，一期可以先不单独抽更多 Port，只需在基础设施层补齐：
- MyBatis Mapper / DAO
- SSE 输出辅助类（如需要）
- 后续扩展 Redis 缓存、任务恢复能力时，再增加缓存适配

### 7.7 推荐实现顺序

为了降低实现风险，建议后端按以下顺序推进：

1. **先补 API DTO**
   - 因为前后端联调首先依赖 DTO 对齐

2. **再补 Trigger Controller**
   - 先把接口路径和返回结构跑通

3. **再补 Domain 骨架**
   - 先建空接口、空服务类，把层次搭起来

4. **最后补 Infrastructure**
   - 接数据库、落仓储实现

5. **SSE 最后闭环**
   - 在草稿保存和任务提交可用后，再把流式生成接起来

### 7.8 第一阶段最低可运行清单

如果目标是尽快形成第一条闭环，后端最低可运行集可以压缩为：

#### 必须实现

```text
POST /drafts/save
GET /drafts/{draftId}
POST /ai-writing/task/submit
GET /ai-writing/task/stream
POST /articles/publish
GET /articles/{articleId}
```

#### 可以第二批补充

```text
GET /drafts/page
POST /drafts/{draftId}/discard
GET /ai-writing/task/{taskId}
GET /articles/page
```

这套顺序对应的就是最核心的业务闭环：

```text
新建草稿 -> 编辑草稿 -> 发起 AI 生成 -> 落稿 -> 发布 -> 查看文章详情
```
