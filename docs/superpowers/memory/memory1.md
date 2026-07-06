# 项目进度记忆 — 2026-07-06

> 本文档记录截止 2026-07-06 的项目状态、已完成工作、关键决策与技术坑，供后续开发参考。

---

## 1. 项目定位与当前阶段

**项目**：AI 技术知识生产与社区平台（sutone-agent-bok）
**第一期目标**：跑通「登录 → 草稿编辑 → 发布 → 文章展示」的 AI 创作发布最小闭环
**当前里程碑**：**M2（内容域闭环）已打通**，M3（AI 写作任务链路）和 M4（前端联调）尚未开始

### 架构概览

```
后端: Java 17 + Spring Boot 3.4.3 + Google ADK 0.5.0 + MyBatis + MySQL 8.4.9
前端: Next.js 16.1.6 (App Router) + React 19 + TailwindCSS 4
分层: api → trigger → domain → infrastructure (DDD)
双域: content（内容生产与发布）+ agent（AI 能力编排）
```

---

## 2. 在此之前已完成的工作

### 2.1 数据基线（M1 ✅）

- 5 张表 DDL 已正式化：`user` / `draft` / `article` / `article_meta` / `ai_task`
- SQL 脚本位置：`docs/dev-ops/mysql/sql/01-sutone-agent-bok-phase1.sql`
- 初始用户：`admin` / `admin`，昵称「苏东昊」

### 2.2 后端 Domain + Infrastructure 层（M2 后端 ✅）

- **Domain Entity**：`DraftEntity`（充血模型，含 initNewDraft / updateContent / discard / markPublished / checkEditable / validateOwner）、`ArticleEntity`（含 publishFromDraft / offline / increaseViewCount）、`ArticleMetaEntity`（含 init / increaseViewCount）
- **Domain VO**：`DraftStatusVO`（EDITING/PUBLISHED/DISCARDED）、`ArticleStatusVO`（PUBLISHED/OFFLINE）
- **Domain Aggregate**：`ContentAggregate`（发布聚合，draft → article 转换）
- **Domain Repository 接口**：`IDraftRepository`、`IArticleRepository`
- **Domain Service**：`DraftDomainService`、`ArticleDomainService`、`PublishDomainService`
- **Command 对象**：`SaveDraftCommand`
- **Infrastructure DAO**：`IDraftDao`、`IArticleDao`、`IArticleMetaDao`（注解式 MyBatis）
- **Infrastructure PO**：`DraftPO`、`ArticlePO`、`ArticleMetaPO`
- **Infrastructure Repository**：`DraftRepository`、`ArticleRepository`（PO↔Entity 转换完整）
- **现有 Agent 体系**：`ChatService`（基于 Google ADK 的对话服务）、`AgentServiceController`（Draw.io/PPT 对话+流式）、`armory` 引擎

---

## 3. 本次对话中完成的工作

### 3.1 单元测试（52 条用例，覆盖 content 域核心代码）

| 测试文件 | 被测类 | 用例数 | 风格 |
|---------|--------|--------|------|
| `DraftEntityTest` | `DraftEntity` | 16 | 纯 JUnit 5 |
| `ArticleEntityTest` | `ArticleEntity` | 9 | 纯 JUnit 5 |
| `ArticleMetaEntityTest` | `ArticleMetaEntity` | 6 | 纯 JUnit 5 |
| `ContentAggregateTest` | `ContentAggregate` | 3 | 纯 JUnit 5 |
| `DraftDomainServiceTest` | `DraftDomainService` | 8 | Mockito mock Repository |
| `ArticleDomainServiceTest` | `ArticleDomainService` | 4 | Mockito mock Repository |
| `PublishDomainServiceTest` | `PublishDomainService` | 4 | Mockito mock Repository |
| `DraftRepositoryTest` | `DraftRepository` | 7 | Mockito mock DAO |
| `ArticleRepositoryTest` | `ArticleRepository` | 7 | Mockito mock DAO |

**结果**：71 tests（含旧有 19 个），0 failures。

### 3.2 后端 API + Trigger 层打通（M2 收尾）

#### Domain Service 注册为 Bean

- `DraftDomainService`、`ArticleDomainService`、`PublishDomainService` 加 `@Service` 注解

#### API 层新增（18 个文件）

**通用**：`PageResponseDTO<T>`

**draft 包**：`SaveDraftRequestDTO`、`SaveDraftResponseDTO`、`DraftDetailResponseDTO`、`DraftPageItemResponseDTO`、`DiscardDraftResponseDTO`

**aiwriting 包**（骨架，M3 接入）：`SubmitAiTaskRequestDTO`、`SubmitAiTaskResponseDTO`、`AiTaskDetailResponseDTO`、`AiWritingChunkDTO`、`AiWritingStreamEventDTO`

**article 包**：`PublishArticleRequestDTO`、`PublishArticleResponseDTO`、`ArticlePageItemResponseDTO`、`ArticleDetailResponseDTO`

**接口**：`IContentDraftService`、`IAiWritingService`、`IArticleService`

#### Trigger 层新增（3 个 Controller）

| Controller | 暴露端点 | 状态 |
|-----------|---------|------|
| `ContentDraftController` | `POST /drafts/save`、`GET /drafts/{id}`、`GET /drafts/page`、`POST /drafts/{id}/discard` | ✅ 可用 |
| `ArticleController` | `POST /articles/publish`、`GET /articles/page`、`GET /articles/{id}` | ✅ 可用 |
| `AiWritingController` | `POST /ai-writing/task/submit`、`GET /ai-writing/task/{id}` | 🔸 骨架占位 |

### 3.3 前端核心页面（12 个文件，6 个页面）

#### 类型定义

- `types/draft.ts`（4 个 DTO）、`types/article.ts`（4 个 DTO）、`types/ai-writing.ts`（7 个类型）
- `types/api.ts`：新增 `PageResponse<T>`

#### API 模块

- `api/drafts.ts`：save / detail / page / discard
- `api/articles.ts`：publish / page / detail
- `api/ai-writing.ts`：submitTask / queryTaskDetail / streamTask（SSE，沿用现有流式解析模式）

#### 页面

| 路由 | 页面 | 说明 |
|------|------|------|
| `/` | 首页（改造） | 「AI 写文章」主入口卡片（首位）+ 真实 API 最近草稿/文章 + Draw.io/PPT 保留 |
| `/drafts` | 草稿箱列表 | 分页列表、新建、废弃 |
| `/drafts/[draftId]` | **草稿编辑页（核心）** | 三段式布局：工具栏 + Markdown 编辑器 + 右侧面板（摘要/封面/AI 占位）；1.5s 防抖自动保存；发布弹窗 |
| `/articles` | 文章广场 | 列表展示 |
| `/articles/[articleId]` | 文章详情 | react-markdown 渲染 + GFM 表格支持 |
| `/me` | 个人中心 | 用户信息 + 最近草稿 + 最近文章 |

#### 构建结果

```
Route (app)
├ ○ /
├ ○ /articles
├ ƒ /articles/[articleId]
├ ○ /drafts
├ ƒ /drafts/[draftId]
├ ○ /drawio
├ ○ /login
├ ○ /me
└ ○ /ppt
```

`npm run build` 通过，无 TS 错误。

### 3.4 基建 bug 修复（3 个关键问题）

| # | Bug | 根因 | 修复 |
|---|-----|------|------|
| 1 | 所有错误返回 `info: null` | `AppException` 4 个构造器都没调 `super(message)`，导致 `getMessage()` 返回 null | 所有构造器加 `super()` 调用 |
| 2 | GET 端点全部 500：`IllegalArgumentException: Name for argument not specified` | `maven-compiler-plugin 3.0`（2012年）没传 `-parameters`，运行时反射拿不到参数名 | 删 version 让 Spring Boot 管（→3.13.0），加 `<parameters>true` |
| 3 | 所有草稿/文章查询报"无权操作" | MyBatis 默认不做下划线转驼峰，`user_id`→`userId` 为 null | `mybatis.configuration.map-underscore-to-camel-case: true` |

### 3.5 环境配置

| 组件 | 状态 | 连接信息 |
|------|------|---------|
| MySQL 8.4.9 | ✅ 运行中（`/Users/suke/.local/mysql/bin/mysqld`） | `127.0.0.1:3306`，root/123456，socket `/tmp/mysql.sock` |
| 数据库 `sutone_agent_bok` | ✅ DDL 已导入 | 5 张表 + 初始用户 admin |
| Maven | ✅ 阿里云 HTTPS 镜像 | `~/.m2/settings.xml` mirror → `https://maven.aliyun.com/repository/public` |
| 后端端口 | 8091 | `application-dev.yml` |
| 前端开发服务器 | 3000（Next.js 默认） | `npm run dev` |

#### 日常启停命令

```bash
# MySQL
nohup /Users/suke/.local/mysql/bin/mysqld \
  --datadir=/Users/suke/.local/mysql/data \
  --user=suke --socket=/tmp/mysql.sock --port=3306 \
  > /tmp/mysql.log 2>&1 &

# 后端
cd sutone-agent-bok/sutone-agent-bok-app && mvn spring-boot:run

# 前端
cd sutone-agent-bok-front && npm run dev
```

### 3.6 端到端验证结果

7 个核心接口全部通过 curl 验证：

```
POST /api/v1/drafts/save          → code:0000, draftId:5
GET  /api/v1/drafts/5             → code:0000, 完整数据含 userId/contentMd
POST /api/v1/drafts/save (更新)    → code:0000, 标题已更新
POST /api/v1/articles/publish     → code:0000, articleId:1, 草稿状态→已发布
GET  /api/v1/articles/1           → code:0000
GET  /api/v1/drafts/page          → code:0000, 3条分页
GET  /api/v1/articles/page        → code:0000, 1篇文章 tags 正确
```

---

## 4. 关键技术决策

1. **先做 content 域再做 AI 链路**：设计文档明确"先让项目成为内容平台，再接入 AI 能力"。Domain 层已经实现了 Draft→Article 完整状态流转，AI 写作是增强而非替代。
2. **Draft 和 Article 物理分离、领域统一**：数据库两张表，但在 `content` 域内统一管理，`PublishDomainService` + `ContentAggregate` 负责转换。
3. **AI 面板用 disabled 占位而不是删掉**：草稿编辑页右侧已经预留 AI 面板 UI，按钮全部 disabled 显示"即将上线"，不提前镀金。
4. **MyBatis 用注解式 SQL 而非 XML**：DAO 层全用 `@Select/@Insert/@Update`，无 XML 映射文件，简单直接。
5. **前端先 Client Component**：登录态走 Cookie，不引入 Next.js middleware，后续再优化 SSR。
6. **`/me/drafts` 和 `/me/articles` 不建独立页面**：内容已合并进 `/me` 主页显示最近条目 + "查看全部"跳转，避免冗余。

---

## 5. 已知问题与待办

### 马上要做的

| 优先级 | 事项 | 说明 |
|--------|------|------|
| P0 | 前端联调验证 | `npm run dev` + 后端跑着，浏览器走通「登录→AI写文章→编辑→发布→看详情」完整链路 |
| P1 | `/drafts/page` 的 updateTime 返回 null | 驼峰映射后可再排查（列表可用，但个别字段为 null） |
| P1 | `ArticleDetailResponseDTO.authorName` 为 null | 文章详情没 join user 表，后续补 |

### M3 阶段（AI 写作链路）

- `AiTaskEntity` + `AiTaskStatusEnum` + `AiWritingTaskTypeEnum`
- `IAiTaskDao` + `AiTaskPO`
- `IAiTaskRepository` + `AiTaskRepository`
- `AiTaskDomainService`（任务生命周期 PENDING→RUNNING→SUCCESS/FAILED）
- `AiWritingDomainService`（对接 armory 引擎，封装写作语义）
- `StreamGenerationDomainService`（SSE 流式事件包装）
- `AiWritingController` 从骨架改造为真实实现

### M4 阶段（前端完善）

- 草稿编辑页实时 Markdown 预览（目前只有编辑区，无预览切换）
- 文章列表/草稿列表分页器组件
- 封面上传能力
- 路由守卫统一抽取 `utils/auth.ts`

### 第二阶段（社区互动）

- 点赞/收藏/评论
- 首页推荐流
- Redis 缓存（当前项目未使用）

---

## 6. 技术坑记录

### 坑 1：AppException 不调 super()

**现象**：所有 Controller 的 catch 块 `e.getMessage()` 返回 null，错误信息丢失。
**根因**：`AppException` 继承 `RuntimeException`，4 个构造器只设置了 `this.code` 和 `this.info`，没调 `super(message)`。
**教训**：自定义异常类必须调 `super(message)`，否则标准 `getMessage()` 返回 null。

### 坑 2：maven-compiler-plugin 3.0 无 -parameters

**现象**：POST 正常，GET 全部 500，报 `IllegalArgumentException: Name for argument not specified`。
**根因**：Spring Boot 3.x 依赖反射获取 `@PathVariable`/`@RequestParam` 参数名，`maven-compiler-plugin 3.0`（2012 年）不传 `-parameters`。
**修复**：删 version 让 spring-boot-starter-parent 管理（→3.13.0），加 `<parameters>true`。

### 坑 3：MyBatis 驼峰映射

**现象**：数据库有 `user_id=1`，但查询后 `entity.userId=null`，所有权限校验失败返回"无权操作"。
**根因**：MyBatis 默认不做 `user_id` → `userId` 转换。
**修复**：`mybatis.configuration.map-underscore-to-camel-case: true`。

### 坑 4：`~/.m2/settings.xml` mirror 错误

**现象**：Maven 无法下载任何插件，所有远程请求返回 `No route to host`。
**根因**：settings.xml 里有个 mirror 把所有请求导向 `http://0.0.0.0/`。
**修复**：替换为阿里云 HTTPS 镜像 `https://maven.aliyun.com/repository/public`。

### 坑 5：surefire 2.6 不支持 JUnit 5

**现象**：`Tests run: 0`，新写的 JUnit 5 测试一个都不执行。
**根因**：pom.xml 里硬编码 `maven-surefire-plugin 2.6`（2009 年）。
**修复**：删 version 让 Spring Boot parent 管，自动升级到 3.5.2。

### 坑 6：MySQL ibdata1 被锁

**现象**：`mysqld` 启动后立即退出，日志报 `Unable to lock ./ibdata1 error: 35`。
**根因**：之前有残留 mysqld 进程持有文件锁。
**修复**：`pkill -9 mysqld` 清理残留后重启。

---

## 7. 文件变更清单

### 本次新建文件

```
后端:
sutone-agent-bok-api/src/main/java/cn/sutone/ai/api/
├── IContentDraftService.java
├── IAiWritingService.java
├── IArticleService.java
└── dto/
    ├── PageResponseDTO.java
    ├── draft/{SaveDraftRequestDTO,SaveDraftResponseDTO,DraftDetailResponseDTO,DraftPageItemResponseDTO,DiscardDraftResponseDTO}.java
    ├── aiwriting/{SubmitAiTaskRequestDTO,SubmitAiTaskResponseDTO,AiTaskDetailResponseDTO,AiWritingChunkDTO,AiWritingStreamEventDTO}.java
    └── article/{PublishArticleRequestDTO,PublishArticleResponseDTO,ArticlePageItemResponseDTO,ArticleDetailResponseDTO}.java

sutone-agent-bok-trigger/src/main/java/cn/sutone/ai/trigger/http/
├── ContentDraftController.java
├── ArticleController.java
└── AiWritingController.java

sutone-agent-bok-app/src/test/java/cn/sutone/ai/test/
├── domain/content/model/entity/{DraftEntityTest,ArticleEntityTest,ArticleMetaEntityTest}.java
├── domain/content/model/aggregate/ContentAggregateTest.java
├── domain/content/service/draft/DraftDomainServiceTest.java
├── domain/content/service/article/ArticleDomainServiceTest.java
├── domain/content/service/publish/PublishDomainServiceTest.java
└── infrastructure/adapter/repository/{DraftRepositoryTest,ArticleRepositoryTest}.java

前端:
sutone-agent-bok-front/src/
├── types/{draft.ts,article.ts,ai-writing.ts}
├── api/{drafts.ts,articles.ts,ai-writing.ts}
└── app/
    ├── drafts/page.tsx
    ├── drafts/[draftId]/page.tsx
    ├── articles/page.tsx
    ├── articles/[articleId]/page.tsx
    └── me/page.tsx
```

### 本次修改文件

```
后端:
sutone-agent-bok/pom.xml                              — compiler-plugin 升级 + aliyun 仓库 HTTPS
sutone-agent-bok-app/pom.xml                          — surefire 升级 + skipTests=false
sutone-agent-bok-app/src/main/resources/application-dev.yml — 库名改为 sutone_agent_bok + 驼峰映射
sutone-agent-bok-domain/.../draft/DraftDomainService.java   — 加 @Service
sutone-agent-bok-domain/.../article/ArticleDomainService.java — 加 @Service
sutone-agent-bok-domain/.../publish/PublishDomainService.java — 加 @Service
sutone-agent-bok-types/.../exception/AppException.java       — 修复 super() 调用

前端:
sutone-agent-bok-front/src/types/api.ts              — 新增 PageResponse<T>
sutone-agent-bok-front/src/app/page.tsx              — 首页改造：AI 写文章入口 + 真实 API
~/.m2/settings.xml                                   — mirror 修复为阿里云 HTTPS
```
