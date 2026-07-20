# 记忆系统 V2 实施计划

> 日期：2026-07-18
> 目标：部署级生产项目 + 面试展示
> V1 状态：全部功能验证通过（LLM 抽取 + 混合检索 + 三层去重 + warm-up）
> V2 工期：13.25 天

---

## 一、整体架构

```
用户 ──▶ Nginx ──▶ Next.js 前端 ──▶ Spring Boot 后端
                                        │
              ┌─────────────────────────┼─────────────────┐
              ▼                         ▼                 ▼
        ┌──────────┐            ┌────────────┐     ┌─────────┐
        │  MySQL 8  │            │  Qdrant    │     │  Redis  │
        │ 记忆文本   │            │  向量检索   │     │  缓存   │
        │ 对话历史   │            │  HNSW 索引  │     │  画像   │
        └──────────┘            └────────────┘     └─────────┘
                                        ▲
                                        │
              ┌─────────────────────────┘
              │
        ┌──────────┐           ┌──────────────┐
        │ SiliconFlow│           │  DeepSeek    │
        │ Embedding │           │  Chat/LLM    │
        │ + Rerank  │           │  抽取 + 写作  │
        └──────────┘           └──────────────┘
```

Docker Compose 服务清单：`app` + `frontend` + `mysql` + `redis` + `qdrant`

---

## 二、模块总览

| 优先级 | 模块 | 工期 | 要点 |
|:---:|------|:---:|------|
| P0 | Qdrant 向量数据库集成 | 3 天 | 持久化/HNSW、双写一致性、迁移、维度探测 |
| P0.5 | 安全加固 + 配置补齐 | 0.5 天 | JWT 鉴权、docker-local/prod 配置 |
| P1 | BGE-Reranker 二阶段精排 | 1.5 天 | 熔断降级、评分融合 |
| P2+P3.5a | Redis 双缓存 + 动态重要性 | 1.5 天 | 画像缓存、搜索缓存、access_count 降级 |
| P3 | Session 恢复 | 1.25 天 | ADK 前置验证、agentId 过滤 DAO |
| P3.5b | LLM 抽取增强 | 0.5 天 | 重试、异步修正、内容长度配置化 |
| P4 | Docker Compose 全栈部署 | 1 天 | dev build / prod 镜像分层 |
| P5 | 前端完善 | 2 天 | 记忆管理页、防抖、API 封装 |
| P5.5 | 测试体系 + 监控埋点 | 2 天 | 单元/集成/性能测试、Micrometer |
| **合计** | | **13.25 天** | |

---

## 三、P0：Qdrant 向量数据库集成（3 天）

### 3.1 选型理由

| 维度 | Qdrant | pgvector | V1 内存 Map |
|------|--------|----------|-------------|
| 部署 | Docker 一行 | 需 PostgreSQL | 无 |
| 持久化 | 磁盘持久化 | 随 PG 持久化 | 重启丢失 |
| 索引 | HNSW（毫秒级） | IVFFlat | O(N) 遍历 |
| 过滤 | payload filter 原生 | WHERE 条件 | 代码层 if |
| 面试 | Mem0 默认选择 | 一般 | "demo 级" |

### 3.2 接入方式

通过 REST API（无需额外 Maven 依赖），Spring `RestTemplate` 直接调用：

```java
@Component
@ConditionalOnProperty(name = "memory.vector-store", havingValue = "qdrant")
public class QdrantVectorStore implements IMemoryVectorStore {

    private final String baseUrl;
    private final String collectionName = "agent_memory";
    private final RestTemplate rest = new RestTemplate();

    @PostConstruct
    public void init() {
        // 维度探测：避免硬编码 1024
        float[] probe = embeddingClient.embed("dimension probe");
        int vectorSize = probe.length;

        // 连接重试：Qdrant 容器可能启动慢于 app（最多 10 次，间隔 2s）
        for (int i = 0; i < 10; i++) {
            try {
                createCollectionIfNotExists(collectionName, vectorSize);
                break;
            } catch (Exception e) {
                if (i == 9) throw e;
                Thread.sleep(2000);
            }
        }
    }

    @Override
    public void insert(Long memoryId, Long userId, float[] embedding, String content, String contentHash) {
        // PUT /collections/agent_memory/points
        // payload 携带 user_id + content，支持过滤和展示
    }

    @Override
    public List<ScoredMemory> search(Long userId, float[] queryEmbedding, int topK) {
        // POST /collections/agent_memory/points/search
        // filter: user_id = userId，返回 cosine 原始分数
        // 分数与 SimpleMemoryVectorStore 保持对齐，复用 MemoryRetriever 融合公式
    }
}
```

### 3.3 实施步骤

| 步骤 | 内容 | 涉及文件 |
|------|------|---------|
| 1 | 配置：`application-dev.yml` / `docker-local.yml` 新增 Qdrant 配置 | YAML 配置文件 |
| 2 | `QdrantVectorStore implements IMemoryVectorStore`（含维度探测 + 连接重试） | `infrastructure/.../QdrantVectorStore.java` |
| 3 | `SimpleMemoryVectorStore` 添加 `@ConditionalOnProperty(memory.vector-store=memory, matchIfMissing=true)` | `SimpleMemoryVectorStore.java` |
| 4 | `IMemoryVectorStore.getVector()` 标记 `@Deprecated`，`MemoryExtractor.findUpdateTarget()` 改用 Qdrant search top-1 | `MemoryExtractor.java` |
| 5 | 全量迁移接口 | `MemoryController.java` |

### 3.4 双写一致性

MySQL 新增 `vector_status` 字段（`SYNCED / PENDING / FAILED`），写入流程：

```sql
ALTER TABLE memory_record
ADD COLUMN vector_status VARCHAR(16) DEFAULT 'SYNCED',
ADD INDEX idx_vector_status (vector_status);
```

```java
// MemoryManager.add() Phase 7：
try {
    memoryRepository.insert(record.withVectorStatus("PENDING"));  // 先写 MySQL
    vectorStore.insert(id, userId, embedding, content, hash);       // 再写 Qdrant
    memoryRepository.updateVectorStatus(id, "SYNCED");             // 标记成功
} catch (Exception e) {
    log.error("Qdrant sync failed: {}", id, e);
    // 不阻断主流程，补偿任务兜底
}
```

补偿任务每 30s 扫描 PENDING 记录重试，3 次失败后标记 FAILED。

### 3.5 数据迁移

```
POST /api/v1/memory/migrate/all?batchSize=50&rateLimit=5
GET  /api/v1/memory/migrate/status
POST /api/v1/memory/migrate/cancel
```

- 每 batch 间 sleep 控制 QPS（硅基流动免费 tier 限制），5000 条约 17 分钟
- 进度持久化优先 Redis，降级本地文件 `/tmp/memory-migrate-progress.json`
- 迁移完成后对比 `COUNT(*) WHERE vector_status='SYNCED'` 与 Qdrant points count
- 迁移窗口（~17 min）内新增记忆暂不入 Qdrant，补偿任务上线后自动修复

### 3.6 配置

```yaml
memory:
  vector-store: ${MEMORY_VECTOR_STORE:memory}   # memory | qdrant
  qdrant:
    url: ${QDRANT_URL:http://localhost:6333}
    collection: agent_memory
    vector-size: 1024                            # 可选，不配则自动探测
```

---

## 四、P0.5：安全加固 + 配置补齐（0.5 天）

### 4.1 安全加固

当前 `/api/v1/memory/**` 和 `/api/v1/writing/chat/**` 完全免鉴权，`userId` 通过 `@RequestParam` 传递，存在跨用户数据访问风险。

| 文件 | 改动 |
|------|------|
| `SecurityConfig.java` | memory、chat 路径改为 `authenticated()` |
| `MemoryController.java` | userId 从 `SecurityContext` 提取（JWT 中已含） |
| `WritingChatController.java` | userId 从 SecurityContext 提取 |
| `AgentServiceController.java` 等 | 一并与排查其他 Controller 是否有同样问题 |

前端 httpOnly cookie 自动携带 JWT，无需改动。

### 4.2 配置补齐

- `application-docker-local.yml` 补充 Redis、memory embedding、Qdrant、Reranker 配置
- `application-prod.yml` 填写完整（数据源/Redis/Qdrant 均通过环境变量注入）
- 所有敏感值通过 `${ENV_VAR}` 注入，不硬编码

```yaml
# application-docker-local.yml 核心新增项
spring.data.redis:
  host: ${REDIS_HOST:127.0.0.1}
  port: ${REDIS_PORT:16379}

memory:
  embedding:
    base-url: ${MEMORY_EMBEDDING_BASE_URL:https://api.siliconflow.cn}
    api-key: ${MEMORY_EMBEDDING_API_KEY}
    model: ${MEMORY_EMBEDDING_MODEL:BAAI/bge-large-zh-v1.5}
    embeddings-path: /v1/embeddings
  vector-store: ${MEMORY_VECTOR_STORE:memory}
  qdrant:
    url: ${QDRANT_URL:http://localhost:6333}
    collection: agent_memory
  reranker:
    enabled: ${MEMORY_RERANKER_ENABLED:false}
    base-url: ${MEMORY_RERANKER_BASE_URL:https://api.siliconflow.cn/v1}
    api-key: ${MEMORY_EMBEDDING_API_KEY}
    model: BAAI/bge-reranker-v2-m3
    timeout: 3000
  cache:
    profile-ttl-minutes: 10
    profile-max-items: 20
    search-ttl-minutes: 2
  extraction:
    max-content-length: 500
  importance:
    base-weight: 0.5
    frequency-weight: 0.3
    recency-weight: 0.2
    min: 0.1
    max: 1.0
```

---

## 五、P1：BGE-Reranker 二阶段精排（1.5 天）

### 5.1 检索流程

```
语义搜索 + BM25 → top-30 粗排 → BGE-Reranker → top-5 精排返回
```

### 5.2 RerankerClient 实现

调用硅基流动 Rerank API（OpenAI 兼容格式），一次传入最多 64 个 documents：

```
POST https://api.siliconflow.cn/v1/rerank
{ "model": "BAAI/bge-reranker-v2-m3", "query": "...", "documents": [...], "top_n": 5 }
```

### 5.3 熔断降级

连续失败 3 次 → 熔断 30s → 返回粗排 top-5，避免雪崩：

```java
private final AtomicInteger failureCount = new AtomicInteger(0);
private volatile long circuitOpenUntil = 0;

public List<ScoredMemory> rerank(String query, List<ScoredMemory> candidates) {
    if (System.currentTimeMillis() < circuitOpenUntil) {
        return candidates.subList(0, Math.min(5, candidates.size()));
    }
    try {
        List<ScoredMemory> result = callRerankApi(query, candidates);
        failureCount.set(0);
        return result;
    } catch (Exception e) {
        if (failureCount.incrementAndGet() >= 3) {
            circuitOpenUntil = System.currentTimeMillis() + 30_000;
        }
        return candidates.subList(0, Math.min(5, candidates.size()));
    }
}
```

### 5.4 评分融合

Reranker Cross-Encoder 分数与 MemoryRetriever 混合分数加权融合：

```
finalScore = 0.6 * rerankerScore + 0.4 * combinedScore
```

### 5.5 配置

```yaml
memory:
  reranker:
    enabled: ${MEMORY_RERANKER_ENABLED:true}
    base-url: ${MEMORY_RERANKER_BASE_URL:https://api.siliconflow.cn/v1}
    api-key: ${MEMORY_EMBEDDING_API_KEY}
    model: BAAI/bge-reranker-v2-m3
    timeout: 3000
```

---

## 六、P2：Redis 双缓存 + P3.5a 动态重要性（1.5 天，合并实施）

> 画像缓存依赖重要性评分。所有记忆 `importance` 当前硬编码 0.5——先做动态重要性，再做缓存。
> 中间态用 `access_count` 降级：取访问次数 top-20。

### 6.1 动态重要性

每次检索命中后，异步更新 importance：

```java
importance = clamp(
    0.5 * baseImportance              // LLM 给出的基础分
    + 0.3 * accessFrequencyScore      // ln(accessCount+1) / ln(100)
    + 0.2 * recencyScore,             // exp(-daysSinceLastAccess / 30)
    0.1, 1.0
);
```

变量通过配置注入，权重、上下界可调。

### 6.2 双缓存架构

| 缓存 | Key | TTL | 用途 |
|------|-----|:--:|------|
| 画像缓存 | `memory:user:{userId}:profile` | 10 min | importance > 0.7 的 top-20，注入 prompt |
| 搜索缓存 | `memory:user:{userId}:search:{queryHash}` | 2 min | 防抖后的重复搜索，减少 embedding 调用 |

### 6.3 检索策略

```
retrieveContext(userId, query):
    if cached = searchCache.get(userId, queryHash):
        return cached                                        // 搜索缓存命中
    profile = profileCache.get(userId)                       // 画像缓存
    results = hybridSearch(query, topK)                      // 实时搜索
    merged  = dedup(profile + results)                       // 画像排最前
    searchCache.set(userId, queryHash, merged)
    return merged
```

### 6.4 失效与降级

| 事件 | 画像缓存 | 搜索缓存 |
|------|:--:|:--:|
| 新增/更新/删除记忆 | 删除 | 不处理（弱一致性） |
| 重要性变化 | 删除 | 不处理 |

Redis 不可用时 try-catch 包裹，跳过缓存不报错。

---

## 七、P3：Session 恢复（1.25 天）

### 7.1 前置验证（P3 第一天先执行）

验证 ADK `InMemoryRunner` 是否支持在创建 Session 时注入历史消息。如果不支持，走备选方案（Prompt 占位符 `{{HISTORY_CONTEXT}}` 或 ADK `BeforeModelCallback` 钩子）。

### 7.2 恢复流程

1. 用户发消息 → 检查 `sessionMap` 是否有 `userId_agentId` 对应 sessionId
2. 没有 → 从 `chat_message` 表加载该用户+agent 最近 20 条消息

### 7.3 关键修正：按 agentId 过滤

`chat_message` 表已有 `agent_id` 字段和 `idx_user_agent` 索引，但现有 `getLastMessages(sessionId)` 只按 sessionId 过滤——重启后 sessionId 已变化。需新增：

```java
// IChatMessageDao 新增 SQL
@Select("SELECT * FROM chat_message WHERE user_id = #{userId} AND agent_id = #{agentId} "
      + "ORDER BY create_time DESC LIMIT #{limit}")
List<ChatMessagePO> selectLastNByUserAgent(...);

// IChatMessageRepository 新增
List<String> getLastMessagesByUserAgent(Long userId, String agentId, int limit);
```

恢复时调用 `getLastMessagesByUserAgent(userId, agentId, 20)` 而非 `getLastMessages(旧sessionId, 20)`。

---

## 八、P3.5b：LLM 抽取增强（0.5 天）

| 问题 | 修正 |
|------|------|
| LLM 调用失败无重试 | `@Retryable(maxAttempts=3, backoff=2000ms)` |
| `content.length() > 200` 硬截断 | 改为配置 `memory.extraction.max-content-length: 500` |
| `updateAccessAsync()` 名有 Async 但同步执行 | 抽出 `MemoryAccessService` 独立类（避免 `@Async` + `@Transactional` 代理失效），加 `@Async("memoryExecutor")` |

---

## 九、P4：Docker Compose 全栈部署（1 天）

### 9.1 分层策略

保留现有推送-部署流程，同时支持本地开发：

| 文件 | 策略 | 用途 |
|------|------|------|
| `docker-compose-environment.yml` | 拉取 Docker Hub 镜像 + **新增 qdrant 服务** | 基础设施（共享） |
| `docker-compose-v2.yml`（新建） | `build: .` 本地构建 | 本地开发一键启动 |
| `docker-compose-app.yml`（改造） | 阿里云预构建镜像 `:2.0`，**新增 Qdrant 环境变量** | 生产部署 |
| `docker-compose-environment-aliyun.yml` | **新增 qdrant（阿里云镜像）** | 阿里云部署 |

### 9.2 基础设施改造

`docker-compose-environment.yml` 新增：

```yaml
qdrant:
  image: qdrant/qdrant:latest
  container_name: qdrant
  ports:
    - "6333:6333"
    - "6334:6334"
  volumes:
    - ./qdrant_storage:/qdrant/storage
  networks:
    - my-network
```

### 9.3 应用层改造

`docker-compose-app.yml` app 服务新增环境变量和依赖：

```yaml
app:
  environment:
    - QDRANT_URL=http://qdrant:6333
    - MEMORY_VECTOR_STORE=qdrant
    - MEMORY_EMBEDDING_API_KEY=${MEMORY_EMBEDDING_API_KEY}
    - MEMORY_RERANKER_ENABLED=true
    - SUTONE_WRITING_MODEL_API_KEY=${SUTONE_WRITING_MODEL_API_KEY}
    - SUTONE_DEEPSEEK_API_KEY=${SUTONE_DEEPSEEK_API_KEY}
    - JWT_SECRET=${JWT_SECRET}
  depends_on: [mysql, redis, qdrant]
```

### 9.4 启动命令

```
# 本地开发
docker compose -f docker-compose-environment.yml -f docker-compose-v2.yml up -d

# 生产部署
docker compose -f docker-compose-environment.yml -f docker-compose-app.yml up -d
```

### 9.5 production 配置

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/sutone_agent_bok
  data.redis:
    host: redis
    port: 6379
memory:
  vector-store: qdrant
  qdrant.url: http://qdrant:6333
  embedding:
    base-url: https://api.siliconflow.cn
    api-key: ${MEMORY_EMBEDDING_API_KEY}
    model: BAAI/bge-large-zh-v1.5
  reranker:
    enabled: true
    base-url: https://api.siliconflow.cn/v1
    api-key: ${MEMORY_EMBEDDING_API_KEY}
    model: BAAI/bge-reranker-v2-m3
```

---

## 十、P5：前端完善（2 天）

### 10.1 记忆管理页面

路由 `/memory`：

```
┌─────────────────────────────────────────────┐
│  🔍 输入关键词...               [搜索] [清除] │
├─────────────────────────────────────────────┤
│  记忆列表 (共 47 条)                  [刷新]  │
│  🟢 偏好 · 用户偏好使用表格对比技术方案   [✕]  │
│  🔵 知识 · 用户是Java工程师，关注JVM...  [✕]  │
│  🟡 事实 · 项目使用Spring Boot 3.4...    [✕]  │
│  ─────────────────────────────────────────  │
│  第 1 页 共 3 页       [上一页] [下一页]     │
└─────────────────────────────────────────────┘
```

### 10.2 搜索防抖

```typescript
const debouncedSearch = useMemo(
  () => debounce(async (query: string) => {
    const res = await memoryApi.search({ q: query, n: 10 });
    setResults(res.items);
  }, 500),  // 500ms 防抖，减少 embedding API 调用
  []
);
```

### 10.3 前端 API 封装

```typescript
// sutone-agent-bok-front/src/api/memory.ts
export const memoryApi = {
  search:  (params: { q: string; n?: number }) => api.get('/api/v1/memory/search', { params }),
  list:    (page: number, pageSize?: number) => api.get('/api/v1/memory/list', { params: { page, pageSize } }),
  detail:  (id: number) => api.get(`/api/v1/memory/${id}`),
  delete:  (id: number) => api.delete(`/api/v1/memory/${id}`),
  refresh: (sessionId: string) => api.post('/api/v1/memory/refresh', null, { params: { sessionId } }),
};
```

安全加固后 userId 从 JWT 提取，前端无需传递。JWT cookie httpOnly 自动携带。

---

## 十一、P5.5：测试体系 + 监控埋点（2 天）

### 11.1 测试金字塔

当前记忆系统 27 个测试文件中完全零覆盖，V2 必须补齐。

| 层级 | 测试内容 | 工具 |
|------|---------|------|
| 单元 | `MemoryRetriever` 评分融合逻辑（mock vectorStore + repository） | JUnit 5 + Mockito |
| 单元 | `MemoryManager` Pipeline 去重/UPDATE 判定 | JUnit 5 + Mockito |
| 单元 | `MemoryExtractor` JSON 解析（正常/markdown包裹/畸形） | JUnit 5 |
| 单元 | `QdrantVectorStore` CRUD + 用户隔离 | Testcontainers Qdrant |
| 单元 | `RerankerClient` 熔断降级 | JUnit 5 + Mockito |
| 集成 | 完整 Pipeline：写入 → 搜索 → 去重 | Spring Boot Test + H2 |
| 集成 | Redis 缓存命中/失效 | Testcontainers Redis |
| 集成 | `MemoryController` REST API | MockMvc |
| 性能 | Qdrant 10 万条 vs SimpleMemoryVectorStore 延迟对比 | JMH |

### 11.2 监控埋点

Spring Boot 3.4 自带 Micrometer（`spring-boot-starter-actuator`），无需额外依赖。

| 指标 | 方式 |
|------|------|
| Qdrant 搜索延迟 | `Timer.builder("memory.search").tag("store","qdrant")` |
| Embedding API 延迟/失败率 | Timer + Counter |
| Reranker API 延迟/熔断状态 | Timer + Gauge |
| Pipeline 各阶段耗时 | `System.currentTimeMillis()` 差值 |
| 线程池队列深度 | `ThreadPoolExecutor.getQueue().size()` |
| Redis 缓存命中率 | 自定义 Counter |

---

## 十二、成本估算

| 项目 | 月成本 | 备注 |
|------|:--:|------|
| 阿里云 ECS 2c4g | ~60 元 | 学生优惠 |
| SiliconFlow embedding | 免费（200万 token/月） | ~2000 条记忆 |
| SiliconFlow rerank | 免费 tier | 够用 |
| DeepSeek API | ~30 元/月 | 抽取 + 写作 |
| **合计** | **~90 元/月** | |

数据迁移成本：1000 条 × 300 token ≈ 30 万 token ≈ 0.21 元（几乎免费）。

---

## 十三、实施时间线

```
Day 1-3：  Qdrant 集成（双写一致性 + 迁移 + 维度探测 + getVector 废弃）
Day 3.5：  安全加固 + 配置补齐
Day 4-5：  Reranker（熔断降级 + 评分融合）
Day 5.5-6：Redis 双缓存 + 动态重要性（合并实施，先重要性再缓存）
Day 6.5：  LLM 抽取增强（重试 + 异步 + 配置化）
Day 7：    Session 恢复（ADK 验证 + agentId DAO）
Day 8：    Docker Compose（dev build / prod 镜像分层）
Day 9-10： 前端（记忆管理页 + 防抖 + API）
Day 11-12：测试 + 监控（单元/集成/性能 + Micrometer）

总计：13.25 天
```

---

## 十四、验证清单

- [ ] `docker compose up -d` 一键启动全部服务
- [ ] 对话后 `memory_record.vector_status = 'SYNCED'` 且 Qdrant 有数据
- [ ] 搜索"JVM"通过向量语义匹配命中"Java工程师"
- [ ] Reranker 精排后 top-5 比粗排更精准
- [ ] Reranker 超时/失败时熔断降级不报错
- [ ] 重启后记忆搜索立即可用（Qdrant 持久化，无 warm-up）
- [ ] 画像缓存命中率 > 80%、搜索缓存命中率 > 40%
- [ ] Redis 不可用时搜索正常降级
- [ ] UPDATE 记忆后画像缓存正确失效
- [ ] MySQL 成功 + Qdrant 失败 → PENDING → 补偿任务修复
- [ ] 重启后继续对话 Agent 理解上下文（按 agentId 正确恢复）
- [ ] 前端记忆管理页可查看/搜索/删除
- [ ] 前端搜索 500ms 防抖，不频繁调用 embedding API
- [ ] memory API 需 JWT 鉴权
- [ ] userId 从 JWT 提取，不接受请求参数
- [ ] `POST /api/v1/memory/migrate/all` 全量迁移 + 速率限制
- [ ] 迁移中断后断点续传
- [ ] Collection 创建前自动探测 embedding 维度
- [ ] `findUpdateTarget()` 改用 Qdrant search 替代逐条 getVector
- [ ] ADK Session 注入方案验证通过
- [ ] `docker-compose-v2.yml` 本地构建可用、`docker-compose-app.yml` 预构建镜像可用
- [ ] 单元测试：MemoryRetriever / MemoryExtractor / QdrantVectorStore / RerankerClient
- [ ] 集成测试：完整 Pipeline 写入到搜索
- [ ] LLM 抽取失败后自动重试 3 次
- [ ] 重要度随访问动态变化

---

## 十五、面试简历描述

```
【项目名称】AI Agent 写作工作台 + 技术社区平台

【技术栈】Java 17 / Spring Boot 3.4 / Google ADK / Spring AI / MySQL 8 /
         Redis / Qdrant / Next.js 15 / Docker

【核心亮点 — Agent 记忆系统】
• 参考 Mem0 开源项目，实现完整的 Agent 长期记忆系统
• 写入侧：V3 分阶段批处理 Pipeline（8 阶段），三层去重（LLM 语义 → MD5 精确 → DB 唯一约束）
• 检索侧：混合检索（Qdrant HNSW 向量 + MySQL FULLTEXT BM25 + 时间衰减 + 重要性加权），
  BGE-Reranker 二阶段精排，画像+搜索双层 Redis 缓存
• 向量存储：Qdrant（HNSW 索引，毫秒级检索），重启无丢失，MySQL 双写 + vector_status 补偿
• Embedding：硅基流动 BGE-large-zh-v1.5（1024 维，自动维度探测）
• 双缓存策略：画像缓存(10min) + 搜索缓存(2min)，Redis 降级保护
• JWT 鉴权，Spring Security 集成
• 全链路 Docker Compose 一键部署：MySQL + Redis + Qdrant + Spring Boot + Next.js
• 测试覆盖：单元测试(Mockito) + 集成测试(Testcontainers) + 性能基准(JMH)
```
