# 记忆系统 — 技术选型与编码计划

> 日期：2026-07-17
> 状态：待实施

---

## 一、技术选型总览

| 维度 | 选型 | 原因 |
|------|------|------|
| **Embedding 客户端** | Spring AI `OpenAiApi`（复用 writing agent 的 embeddings-path 配置） | 项目已有，DeepSeek API 兼容 OpenAI 格式 |
| **向量存储（v1）** | ConcurrentHashMap + 自实现 cosine similarity | 零外部依赖，5K 条以内性能足够，warm-up 从 MySQL 恢复 |
| **向量存储（v2）** | pgvector（PostgreSQL 扩展） | 通过 IMemoryVectorStore 接口切换，不改业务代码 |
| **LLM 调用** | 复用 `OpenAiApi` 的 chat completions 接口 | 和抽取 LLM 走同一个 writing agent 的 api-key 和 base-url |
| **BM25 关键词搜索** | MySQL 8 `FULLTEXT INDEX ... WITH PARSER ngram` | 零外部依赖，中文 ngram 分词开箱即用 |
| **MD5 Hash** | Apache Commons Codec `DigestUtils.md5Hex()` | 项目已在 parent pom dependencyManagement 中声明 1.15 |
| **JSON 解析** | Alibaba Fastjson2（项目已有 2.0.28） | 防御性解析 LLM 输出 |
| **异步执行** | Spring `@Async` + 专用 `ThreadPoolTaskExecutor` bean | 替代 CompletableFuture.runAsync，可管控线程池 |
| **数据库** | MySQL 8（项目已有的 sutone_agent_bok 库） | 新增 3 张表：memory_record、memory_history、chat_message |
| **ORM** | MyBatis 注解模式（`@Select` / `@Insert` / `@Update`） | 项目既有惯例，不用 XML mapper |
| **Prompt 模板存储** | `resources/prompts/memory-extraction.txt` 文件 | 不做成 YAML Agent，单轮调用不需要 |

---

## 二、依赖变更清单

### 2.1 无需新增 Maven 依赖

以下依赖已在项目中存在，直接复用：

| 依赖 | 所在模块 | 用途 |
|------|---------|------|
| `spring-ai-openai` | domain | `OpenAiApi` 调用 embeddings 和 chat completions |
| `mybatis-spring-boot-starter` | infrastructure | DAO 注解 |
| `fastjson` 2.0.28 | domain | JSON 解析 LLM 响应 |
| `commons-codec` 1.15 | (parent pom management) | MD5 hash |
| `lombok` | domain + infrastructure | PO/Entity 简化 |
| `redisson` | domain | Redis 分布式锁 |

### 2.2 需要新增的依赖

**infrastructure/pom.xml** 新增：

```xml
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
</dependency>
```

> `commons-codec` 在 parent pom 的 dependencyManagement 中已声明（1.15），infrastructure 模块不需要指定版本。

---

## 三、模块划分与编码顺序

按依赖关系排序，共 **13 天**，分 12 个步骤：

```
步骤 1-2（基础设施打底）
  │
  ├── 步骤 1: DDL + PO + DAO          [1天]
  ├── 步骤 2: Domain 实体 + 接口       [0.5天]
  │
步骤 3-4（核心组件）
  │
  ├── 步骤 3: MemoryRepository + VectorStore [1.5天]
  ├── 步骤 4: MemoryEmbeddingClient           [0.5天]
  │
步骤 5-7（业务逻辑）
  │
  ├── 步骤 5: MemoryExtractor          [1.5天]
  ├── 步骤 6: MemoryRetriever          [1.5天]
  ├── 步骤 7: MemoryManager            [1天]
  │
步骤 8-11（接入层）
  │
  ├── 步骤 8: MemoryController         [0.5天]
  ├── 步骤 9: ChatService 改造          [1天]
  ├── 步骤 10: Agent YAML + Controller  [1天]
  ├── 步骤 11: 接入记忆系统              [1天]
  │
步骤 12: 端到端验证                     [1.5天]
```

---

## 四、每个步骤的详细设计

### 步骤 1：DDL + PO + DAO（1 天）

**产出文件**：

```
infrastructure/dao/po/MemoryRecordPO.java
infrastructure/dao/po/MemoryHistoryPO.java
infrastructure/dao/po/ChatMessagePO.java
infrastructure/dao/IMemoryRecordDao.java
infrastructure/dao/IMemoryHistoryDao.java
infrastructure/dao/IChatMessageDao.java
```

**DDL**（在与方案文档中一致）：

```sql
-- 1.1 memory_record（已有 DDL，新增 content_tokenized 和 FULLTEXT）
-- 1.2 memory_history（不变）
-- 1.3 chat_message（新增）
```

**MemoryRecordPO**：
```java
@Data
public class MemoryRecordPO {
    private Long id;
    private Long userId;
    private String type;              // fact / preference / knowledge / event
    private String content;           // 记忆原文，≤512 字
    private String contentHash;       // MD5(content)
    private String contentTokenized;  // 分词结果，供 FULLTEXT
    private String sourceSessionId;
    private Double importance;        // 默认 0.5
    private Integer accessCount;      // 默认 0
    private LocalDateTime lastAccessedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;        // 0=正常 1=删除
}
```

**IMemoryRecordDao** 核心方法：
```java
@Mapper
public interface IMemoryRecordDao {
    int insert(MemoryRecordPO po);           // 普通 INSERT
    int updateContent(...);                  // UPDATE content + content_hash
    MemoryRecordPO selectById(Long id);
    List<MemoryRecordPO> selectByUserId(Long userId, int offset, int limit);
    List<MemoryRecordPO> selectAllActive();  // warm-up 用：所有 is_deleted=0
    List<MemoryRecordPO> fulltextSearch(Long userId, String query, int limit); // BM25
    int deleteById(Long id);                 // 逻辑删除
}
```

**FULLTEXT SQL 示例**：
```java
@Select("""
    SELECT id, user_id, content, content_hash, MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS match_score
    FROM memory_record
    WHERE user_id = #{userId} AND is_deleted = 0
      AND MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
    ORDER BY match_score DESC
    LIMIT #{limit}
    """)
List<MemoryRecordPO> fulltextSearch(@Param("userId") Long userId, @Param("query") String query, @Param("limit") int limit);
```

**IChatMessageDao** 核心方法：
```java
@Mapper
public interface IChatMessageDao {
    int insert(ChatMessagePO po);
    List<ChatMessagePO> selectLastN(@Param("sessionId") String sessionId, @Param("limit") int limit);
}
```

---

### 步骤 2：Domain 层实体 + 接口（0.5 天）

**产出文件**：

```
domain/agent/model/entity/MemoryRecordEntity.java
domain/agent/model/valobj/MemoryTypeVO.java
domain/agent/adapter/repository/IMemoryRepository.java
domain/agent/adapter/repository/IMemoryVectorStore.java
```

**MemoryTypeVO**：
```java
public enum MemoryTypeVO {
    FACT("fact"),           // 用户客观事实
    PREFERENCE("preference"), // 写作偏好
    KNOWLEDGE("knowledge"),  // 技术知识点
    EVENT("event");          // 事件记录

    private final String code;
    
    public static boolean isValid(String type) {
        return Arrays.stream(values()).anyMatch(v -> v.code.equals(type));
    }
}
```

**IMemoryVectorStore 接口**（5 个方法，简洁可插拔）：
```java
public interface IMemoryVectorStore {
    void insert(Long memoryId, Long userId, float[] embedding, String content, String contentHash);
    void update(Long memoryId, float[] newEmbedding, String newContent);
    List<ScoredMemory> search(Long userId, float[] queryEmbedding, int topK);
    void delete(Long memoryId);
    float[] getVector(Long memoryId);  // 用于 UPDATE 判定时获取已有向量
}
```

**IMemoryRepository 接口**：
```java
public interface IMemoryRepository {
    Long nextId();
    void insert(MemoryRecordEntity record);
    void updateContent(Long id, String newContent, String newHash);
    MemoryRecordEntity queryById(Long id);
    List<MemoryRecordEntity> queryByUserId(Long userId, int offset, int limit);
    int countByUserId(Long userId);
    void deleteById(Long id);
    List<MemoryRecordEntity> selectAllActive();
    List<MemoryRecordEntity> fulltextSearch(Long userId, String query, int limit);
    void batchUpdateAccessInfo(List<Long> ids);
    void insertHistory(Long memoryId, String oldContent, String newContent, String event, String sessionId);
    List<String> getLastMessages(String sessionId, int limit);
}
```

**辅助值对象**：
```java
// ScoredMemory — 带分数的记忆检索结果
public record ScoredMemory(
    Long id, 
    String content, 
    double score,
    Double importance,
    LocalDateTime lastAccessedAt
) {}

// MemoryCandidate — LLM 抽取的候选记忆
public record MemoryCandidate(
    String content, 
    String type,           // fact/preference/knowledge/event
    String attributedTo    // user/agent
) {}

// MemoryItem — 对外暴露的记忆对象
@Data
public class MemoryItem {
    private Long id;
    private String content;
    private String type;
    private Double score;       // 检索时才有
    private Double importance;
    private Integer accessCount;
    private LocalDateTime createTime;
}
```

---

### 步骤 3：MemoryRepository + SimpleMemoryVectorStore（1.5 天）

**产出文件**：

```
infrastructure/adapter/repository/MemoryRepository.java
infrastructure/adapter/repository/SimpleMemoryVectorStore.java
```

**SimpleMemoryVectorStore 核心**：

```java
@Component
public class SimpleMemoryVectorStore implements IMemoryVectorStore {

    // id -> (userId, embedding, content, contentHash)
    private final ConcurrentHashMap<Long, VectorEntry> store = new ConcurrentHashMap<>();

    @Override
    public void insert(Long memoryId, Long userId, float[] embedding, String content, String contentHash) {
        store.put(memoryId, new VectorEntry(userId, embedding, content, contentHash));
    }

    @Override
    public List<ScoredMemory> search(Long userId, float[] queryEmbedding, int topK) {
        // 1. 过滤该用户的记忆
        // 2. 逐条计算 cosine similarity
        // 3. 用 PriorityQueue 取 top-K（避免全量排序）
        PriorityQueue<ScoredMemory> pq = new PriorityQueue<>(topK, Comparator.comparingDouble(ScoredMemory::score));
        for (Map.Entry<Long, VectorEntry> entry : store.entrySet()) {
            if (!entry.getValue().userId().equals(userId)) continue;
            double score = cosine(queryEmbedding, entry.getValue().embedding());
            if (pq.size() < topK) {
                pq.offer(new ScoredMemory(entry.getKey(), entry.getValue().content(), score, ...));
            } else if (score > pq.peek().score()) {
                pq.poll();
                pq.offer(new ScoredMemory(entry.getKey(), entry.getValue().content(), score, ...));
            }
        }
        // 4. 返回排序结果
        return pq.stream().sorted(Comparator.comparingDouble(ScoredMemory::score).reversed()).toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() { /* 前文已有实现 */ }
}
```

**cosine similarity**：
```java
private static double cosine(float[] a, float[] b) {
    double dot = 0.0, normA = 0.0, normB = 0.0;
    for (int i = 0; i < a.length; i++) {
        dot += (double) a[i] * b[i];
        normA += (double) a[i] * a[i];
        normB += (double) b[i] * b[i];
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
}
```

---

### 步骤 4：MemoryEmbeddingClient（0.5 天）

**产出文件**：

```
infrastructure/gateway/MemoryEmbeddingClient.java
```

**设计思路**：不从 `AiAgentRegisterVO` 中取 `OpenAiApi`（那是 per-agent 的、不便复用），而是直接读 `AiAgentAutoConfigProperties` 获取 writing agent 的 YAML 配置中的 `ai-api` 字段，自己构建一个专用的 `OpenAiApi` bean。

```java
@Component
public class MemoryEmbeddingClient {

    private final OpenAiApi openAiApi;
    private final String model;

    public MemoryEmbeddingClient(AiAgentAutoConfigProperties properties) {
        // 从 writingAgent 的配置中获取 ai-api 信息
        AiAgentConfigTableVO writingConfig = properties.getTables().get("writingAgent");
        AiAgentConfigTableVO.Module.AiApi apiConfig = writingConfig.getModule().getAiApi();
        
        this.openAiApi = OpenAiApi.builder()
            .baseUrl(apiConfig.getBaseUrl())
            .apiKey(apiConfig.getApiKey())
            .embeddingsPath(apiConfig.getEmbeddingsPath())
            .build();
        this.model = writingConfig.getModule().getChatModel().getModel();
    }

    public float[] embed(String text) {
        ResponseEntity<EmbeddingResponse> resp = openAiApi.embeddings(
            new EmbeddingRequest(List.of(text), model));
        List<Double> vec = resp.getBody().getData().get(0).getEmbedding();
        return toFloatArray(vec);  // double -> float
    }

    public List<float[]> embedBatch(List<String> texts) {
        ResponseEntity<EmbeddingResponse> resp = openAiApi.embeddings(
            new EmbeddingRequest(texts, model));
        return resp.getBody().getData().stream()
            .map(e -> toFloatArray(e.getEmbedding()))
            .toList();
    }

    private static float[] toFloatArray(List<Double> doubles) {
        float[] arr = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) arr[i] = doubles.get(i).floatValue();
        return arr;
    }
}
```

> **为什么用 float[] 而不是 double[]**：DeepSeek embedding 输出 1024 维，float[] 4KB vs double[] 8KB，对于 5000 条记忆差 20MB 内存。cosine 相似度计算精度用 float 足够。

---

### 步骤 5：MemoryExtractor（1.5 天）

**产出文件**：

```
domain/agent/service/memory/MemoryExtractor.java
resources/prompts/memory-extraction.txt
```

**职责**：
1. 加载 prompt 模板
2. 用已有记忆 + 最近上下文 + 新消息构建完整 prompt
3. 调用 `OpenAiApi` 的 chat completions（复用 MemoryEmbeddingClient 中的 OpenAiApi）
4. 防御性 JSON 解析
5. UPDATE 判定逻辑

**Prompt 模板**（`resources/prompts/memory-extraction.txt`）：基于方案文档的合成。

**核心方法签名**：
```java
@Component
public class MemoryExtractor {
    
    private final MemoryEmbeddingClient embeddingClient;
    private final OpenAiApi openAiApi;  // 同一个 api-key 的 chat completions
    private final String extractionPromptTemplate;  // 从文件加载

    // 抽取 + UPDATE 判定
    public List<MemoryCandidate> extract(
        List<MemoryRecord> existingMemories,
        List<Map<String, String>> newMessages,
        List<String> lastMessages
    );
    
    // 防御性 JSON 解析（内部使用）
    private List<MemoryCandidate> parseResponse(String llmResponse);
    
    // 判断是否需要 UPDATE（cosine > 0.9）
    private boolean shouldUpdate(MemoryCandidate candidate, MemoryRecord existing, float[] candidateEmbedding);
}
```

**LLM 调用方式**：
```java
// 使用 Spring AI 的 OpenAiApi
ResponseEntity<ChatCompletion> response = openAiApi.chatCompletionEntity(
    new ChatCompletionRequest(
        List.of(
            new ChatCompletionMessage("system", systemPrompt),
            new ChatCompletionMessage("user", userPrompt)
        ),
        model,
        0.3,  // temperature 低，要求稳定输出
        Map.of("type", "json_object")  // DeepSeek 支持 response_format
    )
);
```

---

### 步骤 6：MemoryRetriever（1.5 天）

**产出文件**：

```
domain/agent/service/memory/MemoryRetriever.java
```

**职责**：完整的混合检索 pipeline（@3.3 节中的完整 search 方法）

**核心方法签名**：
```java
@Component
public class MemoryRetriever {
    
    // 混合检索
    public List<MemoryItem> search(Long userId, String query, int topK, double threshold);
    
    // 为 Agent prompt 格式化记忆上下文（注入用）
    public String retrieveFormattedContext(Long userId, String queryContext, int topK);
    
    // BM25 归一化
    private double sigmoidNormalize(double rawScore, double maxScore);
    
    // 时间衰减
    private double computeRecencyBoost(LocalDateTime lastAccessedAt);
    
    // 异步更新 access_count 和 last_accessed_at
    private void batchUpdateAccessInfo(List<Long> hitIds);
}
```

**retrieveFormattedContext 输出格式**：
```
- 用户是 Java 后端工程师，技术栈 Java 17 + Spring Boot 3.4 + MySQL 8.0
- 写作偏好：喜欢用 Markdown 表格对比方案，标题带数字编号
- 最近在做微服务拆分项目
```

---

### 步骤 7：MemoryManager（1 天）

**产出文件**：

```
domain/agent/service/memory/MemoryManager.java
```

**职责**：Pipeline 编排门面，对外暴露简洁 API

```java
@Component
public class MemoryManager {

    // 写入 pipeline（完整 8 阶段）
    @Async("memoryExecutor")
    public void addAsync(Long userId, Long agentId, String sessionId, 
                         List<Map<String, String>> messages);

    // 搜索
    public List<MemoryItem> search(Long userId, String query, int topK);

    // CRUD
    public MemoryRecord get(Long memoryId);
    public void delete(Long memoryId);
    public PageResult<MemoryItem> list(Long userId, int page, int pageSize);
}
```

---

### 步骤 8：MemoryController（0.5 天）

**产出文件**：

```
api/IMemoryService.java
api/dto/memory/MemoryItemDTO.java
api/dto/memory/MemorySearchRequest.java
api/dto/memory/MemoryListResponse.java
trigger/http/MemoryController.java
```

**API 设计**：
```
GET    /api/v1/memory/search?q=JVM&n=5     → 语义搜索
GET    /api/v1/memory/list?page=1&size=20   → 分页列表
GET    /api/v1/memory/{id}                  → 详情
DELETE /api/v1/memory/{id}                  → 删除
POST   /api/v1/memory/refresh?sessionId=x   → 手动触发重抽取
```

---

### 步骤 9：ChatService 改造（1 天）

**改造文件**：

```
domain/agent/service/chat/ChatService.java
infrastructure/dao/IChatMessageDao.java
infrastructure/dao/po/ChatMessagePO.java
```

**改造内容**：
1. 在 `handleMessageStream()` 中，调用 ADK runner 前后持久化用户消息和 AI 回复
2. AI 回复的持久化放在 Flowable 的 `doOnComplete()` 回调中
3. 持久化走 `IChatMessageDao.insert()`，非阻塞

---

### 步骤 10：写作 Agent YAML + 新 Controller（1 天）

**产出文件**：

```
resources/agent/agent-writing-chat.yml     # 新的多轮对话 Agent
trigger/http/WritingChatController.java
```

**新 YAML 结构**：单 Agent 多轮对话模式（@13.4 节已有设计）。

---

### 步骤 11：接入记忆系统（1 天）

**改造文件**：

```
domain/agent/service/ai_writing/AiWritingService.java
```

**两个改动点**：

1. **对话开始时注入**：在 `buildPrompt()` 或 ChatService 组装 context 时调用 `memoryRetriever.retrieveFormattedContext()`，拼接到 system prompt 或当前消息前缀
2. **用户保存时触发**：在新 Controller 的 save 接口中调用 `memoryManager.addAsync()`

```java
// 保存草稿接口
@PostMapping("/writing/save")
public Response<?> save(@RequestBody SaveRequest req) {
    // 1. 保存草稿
    draftDomainService.saveDraft(...);
    
    // 2. 异步触发记忆抽取
    List<Map<String, String>> messages = chatMessageDao
        .selectLastN(req.getSessionId(), 20).stream()
        .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
        .toList();
    memoryManager.addAsync(userId, agentId, req.getSessionId(), messages);
    
    return Response.success();
}
```

---

### 步骤 12：端到端验证（1.5 天）

按验证清单逐项测试：
1. 多轮对话 + chat_message 表记录
2. 三层去重 + UPDATE 判定
3. 混合检索（语义 + BM25）
4. 重启 warm-up
5. 记忆注入到新的写作对话
6. history 审计

---

## 五、关键设计决策

### 5.1 OpenAiApi 如何复用

当前 `AiApiNode` 中创建的 `OpenAiApi` 存在 `DynamicContext` 中，不适合全局复用。

**方案**：`MemoryEmbeddingClient` 直接读 `AiAgentAutoConfigProperties` 获取 writing agent 的 api-key、base-url、embeddings-path，构建一个独立的 `OpenAiApi` 实例。这和 `AiApiNode` 用的是同一套配置，只是实例独立。

### 5.2 向量库接口设计

```java
public interface IMemoryVectorStore {
    void insert(Long memoryId, Long userId, float[] embedding, String content, String contentHash);
    List<ScoredMemory> search(Long userId, float[] queryEmbedding, int topK);
    void delete(Long memoryId);
    void update(Long memoryId, float[] newEmbedding, String newContent);
    float[] getVector(Long memoryId);
}
```

5 个方法，v1 用 ConcurrentHashMap 实现，v2 用 pgvector 实现，切换时只需改 Spring Bean 注入。

### 5.3 异步线程池

在 `ThreadPoolConfig` 中新增 `memoryExecutor` bean：

```java
@Bean("memoryExecutor")
public TaskExecutor memoryExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("memory-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

> 注意：这里是 `ThreadPoolTaskExecutor`（Spring 的），不是 `ThreadPoolExecutor`（JDK 的）。`@Async` 需要前者。

### 5.4 记忆类型枚举

**对齐 Mem0**：Mem0 的 ADDITIVE_EXTRACTION_PROMPT 中记忆没有 type 字段，只有 attributed_to 和 text。本项目加了 type 字段（fact/preference/knowledge/event）作为增强，在 prompt 中定义类型规则。

### 5.5 FULLTEXT 注意事项

MySQL 8 ngram parser 默认 `ngram_token_size=2`，即每 2 个字一个词元。对中文短记忆（50 字）效果足够。如果检索效果不好，可以调小为 1。

```sql
-- 查看当前配置
SHOW VARIABLES LIKE 'ngram_token_size';

-- 如需 1-gram（每单字一个词元，查询更灵活）
-- 需在 my.cnf 中设置 ngram_token_size=1 并重启
```

---

## 六、文件产出清单（总计 ~25 个文件）

| 模块 | 文件 | 类型 |
|------|------|:---:|
| **DDL** | 3 张表 DDL | SQL |
| **infrastructure/dao/po** | MemoryRecordPO, MemoryHistoryPO, ChatMessagePO | 新增 |
| **infrastructure/dao** | IMemoryRecordDao, IMemoryHistoryDao, IChatMessageDao | 新增 |
| **infrastructure/adapter/repository** | MemoryRepository, SimpleMemoryVectorStore | 新增 |
| **infrastructure/gateway** | MemoryEmbeddingClient | 新增 |
| **domain/model/entity** | MemoryRecordEntity | 新增 |
| **domain/model/valobj** | MemoryTypeVO, ScoredMemory | 新增 |
| **domain/adapter/repository** | IMemoryRepository, IMemoryVectorStore | 新增 |
| **domain/service/memory** | MemoryManager, MemoryExtractor, MemoryRetriever | 新增 |
| **domain/service/chat** | ChatService（改造：新增对话持久化） | 修改 |
| **domain/service/ai_writing** | AiWritingService（改造：注入记忆） | 修改 |
| **trigger/http** | MemoryController, WritingChatController | 新增 |
| **api** | IMemoryService, dto/* | 新增 |
| **resources** | prompts/memory-extraction.txt, agent/agent-writing-chat.yml | 新增 |
| **config** | ThreadPoolConfig（新增 memoryExecutor） | 修改 |

---

## 七、配置项设计

### 7.1 application-dev.yml 新增配置

```yaml
# 记忆系统配置
memory:
  search:
    threshold: 0.1          # 语义分门控阈值
    over-fetch-factor: 4    # over-fetch 倍数
    default-top-k: 5        # 默认返回条数
  extraction:
    temperature: 0.3        # LLM 抽取 temperature
    update-threshold: 0.9   # cosine > 此值走 UPDATE
  warm-up:
    enabled: true           # 启动时是否 warm-up
    batch-size: 50          # 每批 embed 条数
```

### 7.2 配置类

```java
@Data
@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryConfigProperties {
    private Search search = new Search();
    private Extraction extraction = new Extraction();
    private WarmUp warmUp = new WarmUp();

    @Data
    public static class Search {
        private double threshold = 0.1;
        private int overFetchFactor = 4;
        private int defaultTopK = 5;
    }

    @Data
    public static class Extraction {
        private double temperature = 0.3;
        private double updateThreshold = 0.9;
    }

    @Data
    public static class WarmUp {
        private boolean enabled = true;
        private int batchSize = 50;
    }
}
```

---

## 八、异常处理与降级策略

| 异常场景 | 处理方式 | 影响范围 |
|---------|---------|---------|
| Embedding API 超时/失败 | log.warn + 跳过本次抽取，不阻断主流程 | 本次记忆丢失，下次可补 |
| LLM 抽取 JSON 解析失败 | 正则提取 + fastjson 容错，失败返回空列表 | 本次记忆丢失 |
| LLM 抽取返回空（无值得记的） | 正常情况，直接 return | 无影响 |
| MySQL DuplicateKeyException | catch + skip，继续处理批次内其他记忆 | 单条跳过 |
| 向量库 insert 失败 | log.error + 跳过，MySQL 中记忆已写入（重启 warm-up 可恢复） | 暂时不可搜索 |
| 向量库 search 返回空 | 正常情况（新用户无记忆），不注入记忆上下文 | 无影响 |
| warm-up 时 Embedding API 不可用 | 延迟 30s 重试 3 次，全部失败则标记向量库为空，记忆可读（MySQL list）但不可搜 | 搜索功能暂不可用 |
| chat_message 持久化失败 | log.warn，不影响对话流程（对话仍正常，只是记忆系统失去上下文） | 抽取时上下文不完整 |

---

## 九、测试策略

### 9.1 单元测试（步骤 3-7 同步编写）

| 测试类 | 测试点 |
|--------|--------|
| `SimpleMemoryVectorStoreTest` | cosine 计算正确性、search top-K 排序、并发 insert + search |
| `MemoryExtractorTest` | JSON 解析各种异常格式（code fence、多余文字、空响应）、type 枚举校验 |
| `MemoryRetrieverTest` | 评分融合公式验证、门控阈值、时间衰减曲线、BM25 归一化 |
| `MemoryManagerTest` | Pipeline 各阶段单测、去重逻辑、UPDATE 判定 |

### 9.2 集成测试（步骤 12）

使用 `@SpringBootTest` + 真实 MySQL + mock Embedding API：
1. 写入 3 条不同类型记忆 → 验证 MySQL + 向量库一致性
2. 写入重复内容 → 验证三层去重
3. 写入近似内容（cosine > 0.9）→ 验证 UPDATE
4. search 验证混合检索排序
5. 重启后 warm-up → 验证搜索恢复

---

## 十、开发环境准备清单

开始编码前确认以下环境就绪：

- [ ] MySQL 8 运行中（localhost:3306），database `sutone_agent_bok` 存在
- [ ] Redis 运行中（localhost:16379）
- [ ] DeepSeek API Key 有效（环境变量 `SUTONE_WRITING_MODEL_API_KEY`）
- [ ] DeepSeek embeddings 接口可用（`curl -X POST https://api.deepseek.com/embeddings ...`）
- [ ] 项目 `mvn compile` 通过
- [ ] MySQL `ngram_token_size` 确认（默认 2，可选调为 1）
