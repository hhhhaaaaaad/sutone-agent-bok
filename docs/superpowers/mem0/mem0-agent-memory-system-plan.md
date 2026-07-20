# Agent 记忆系统实施方案（基于 Mem0 v2.0.12）

> 编写日期：2026-07-17
> 评审修订：2026-07-17
> 参考项目：Mem0 v2.0.12（`/Users/suke/agent/sutone/mem0`）
> 核心源码：`mem0/memory/main.py`、`mem0/configs/prompts.py`、`mem0/memory/storage.py`

---

## 一、项目现状与问题

### 1.1 当前记忆机制

项目使用 Google ADK `InMemoryRunner`，底层是 `InMemorySessionService`：

```java
// ChatService.java — 当前实现
Session session = runner.sessionService().createSession(appName, userId).blockingGet();
// Session 存在 JVM 堆内存的 ConcurrentHashMap 中
```

两个硬伤：

| 问题 | 影响 |
|------|------|
| 服务重启即丢失 | `ConcurrentHashMap` 在进程内，重启清零 |
| 无跨会话记忆 | 每次写作任务创建新 session，Agent 不记得用户偏好、写作风格、历史技术栈 |

### 1.2 目标

参考 Mem0 设计思想，实现一个：
- **持久化**（MySQL，重启不丢）
- **跨会话**（写作任务 A 的记忆能影响任务 B）
- **语义检索**（embedding + 余弦相似度）
- **自动抽取**（LLM 从对话中提取结构化记忆）

的 Agent 记忆系统。

---

## 二、Mem0 源码剖析

### 2.1 核心架构

Mem0 的核心类是 `Memory`（`mem0/memory/main.py:448`），由三个可插拔组件构成：

```python
class Memory(MemoryBase):
    def __init__(self, config):
        self.embedding_model = EmbedderFactory.create(provider, config)  # 向量化
        self.vector_store   = VectorStoreFactory.create(provider, config)  # 向量存储
        self.llm            = LlmFactory.create(provider, config)  # 文本抽取
        self.db             = SQLiteManager(history_db_path)  # 历史/消息存储
```

### 2.2 V3 分阶段批处理 Pipeline（`_add_to_vector_store`，main.py:835）

这是 Mem0 最核心的设计。写入记忆走 8 个阶段：

```
Phase 0: 上下文收集
  └ 从 SQLite messages 表取该 session 最近 10 条消息

Phase 1: 已有记忆检索（用于传入 LLM 做去重）
  └ embed 新消息 → 向量库 search topK=10 → 得到已有记忆列表

Phase 2: LLM 抽取（单次调用）
  └ system_prompt + user_prompt（含 已有记忆+新消息+最近上下文）
  └ response_format={"type": "json_object"}
  └ 返回 {"memory": [{"text":"...","type":"...","attributed_to":"..."}]}

Phase 3: 批量 embedding
  └ embedding_model.embed_batch(texts) → 失败则逐条 fallback

Phase 4: Hash 去重
  └ MD5(text) → 与已有 hashes + 批次内 seen_hashes 对比 → 跳过重复

Phase 5-6: 批量持久化
  └ 向量库 batch insert + SQLite batch history

Phase 7: 实体链接（可选）
  └ spaCy 提取实体 → 独立 entity_store → 实体→记忆关联

Phase 8: 保存原始消息
  └ SQLite messages 表保存，供下次 Phase 0 使用
```

**关键设计意图**：Phase 1 把已有记忆传给 LLM，让 LLM 在抽取时就知道「哪些已经有了」，实现第一道去重。Mem0 的去重总共三层：

```
第一层（LLM自主）: prompt 中包含已有记忆 → LLM 避免重复抽取
第二层（Hash精确）: MD5(content) → 精确匹配
第三层（语义相似）: cosine > 0.9 → 判定为更新而非新增（本项目 v1 实现）
```

### 2.3 混合搜索 + 评分融合（`_search_vector_store`，main.py:1584）

```
Step 1: 查询预处理（lemmatize + 实体提取）
Step 2: embed 查询
Step 3: 语义搜索（over-fetch limit × 4，不去直接截断）
Step 4: BM25 关键词搜索（如果向量库支持）
Step 5: 实体 boosting（从 entity_store 检索）
Step 6: score_and_rank() 融合：
  combined = (semantic + bm25 + entity_boost) / max_possible
  gate：semantic score >= threshold(0.1)
Step 7: 可选 reranker（Cohere/SentenceTransformer/LLM）
```

**关键设计意图**：over-fetch 4 倍后在内存中精排，避免向量库直接截断导致漏召回。

### 2.4 记忆抽取 Prompt 设计

Mem0 的抽取 prompt 分为两个版本：
- `USER_MEMORY_EXTRACTION_PROMPT`：只从 user 消息提取
- `AGENT_MEMORY_EXTRACTION_PROMPT`：只从 assistant 消息提取
- `ADDITIVE_EXTRACTION_PROMPT`：V3 统一版本，支持去重、链接、时间

prompt 的核心指令模式：
```
Types of Information to Remember:
1. Store Personal Preferences
2. Maintain Important Personal Details
3. Track Plans and Intentions
...

Few-shot examples:
Input: Hi.
Output: {"facts" : []}

Input: Hi, my name is John. I am a software engineer.
Output: {"facts" : ["Name is John", "Is a Software engineer"]}
```

### 2.5 消息历史持久化（`SQLiteManager`，storage.py）

```python
# messages 表：保留最近 10 条消息，按 session_scope 分区
last_messages = self.db.get_last_messages(session_scope, limit=10)

# history 表：记忆变更日志
{
    "memory_id": "...",
    "old_memory": "...",
    "new_memory": "...",
    "event": "ADD" | "UPDATE" | "DELETE",
    "created_at": "..."
}
```

---

## 三、Java 适配方案

### 3.1 架构映射

```
Mem0 (Python)                         本项目 (Java)
─────────────                         ────────────
Memory.add(messages, user_id)   →    MemoryManager.add(userId, sessionId, messages)
Memory.search(query, filters)   →    MemoryManager.search(userId, query, topK)
Memory.get(memory_id)           →    MemoryManager.get(memoryId)
Memory.update(memory_id, text)  →    MemoryManager.update(memoryId, content)
Memory.delete(memory_id)        →    MemoryManager.delete(memoryId)
Memory.get_all(filters)         →    MemoryManager.list(userId)

EmbedderFactory.create()        →    MemoryEmbeddingClient (单实现)
LlmFactory.create()             →    复用 ChatService (ADK)
VectorStoreFactory.create()     →    IMemoryVectorStore 接口 + 双实现
SQLiteManager.history           →    memory_history 表
SQLiteManager.get_last_messages →    memory_history 表按 session_id 查
```

### 3.2 V3 Pipeline 的 Java 实现（完整版，保留 8 阶段）

```java
/**
 * 对应 Mem0._add_to_vector_store()，走 V3 完整 Pipeline
 */
@Async("memoryExecutor")
public void add(Long userId, Long agentId, String sessionId,
                List<Map<String, String>> messages) {

    // Phase 0: 上下文收集
    // 从 chat_message 表取该 session 最近 10 条消息
    List<ChatMessagePO> lastMessages = chatMessageDao.selectLastN(sessionId, 10);

    // Phase 1: 已有记忆检索（传入 LLM 做去重）
    String combinedText = messages.stream()
        .map(m -> m.get("content")).collect(Collectors.joining("\n"));
    float[] queryEmbedding = embeddingClient.embed(combinedText);
    List<MemoryRecord> existingMemories = vectorStore.search(userId, queryEmbedding, 10);

    // Phase 2: LLM 抽取（单次调用，传入已有记忆，LLM 自主避免重复）
    String prompt = buildExtractionPrompt(existingMemories, messages, lastMessages);
    String llmResponse = memoryLLM.generate(prompt);
    List<MemoryCandidate> candidates = parseExtractionResponse(llmResponse);

    if (candidates.isEmpty()) return;

    // Phase 3: 批量 embedding
    List<String> texts = candidates.stream().map(MemoryCandidate::content).toList();
    List<float[]> embeddings = embeddingClient.embedBatch(texts);

    // Phase 4: Hash 去重（MD5）
    Set<String> existingHashes = existingMemories.stream()
        .map(MemoryRecord::getContentHash).collect(Collectors.toSet());
    Set<String> batchHashes = new HashSet<>();
    List<MemoryRecord> toInsert = new ArrayList<>();

    for (int i = 0; i < candidates.size(); i++) {
        String hash = md5(texts.get(i));
        if (existingHashes.contains(hash) || batchHashes.contains(hash)) {
            continue;
        }
        batchHashes.add(hash);
        toInsert.add(MemoryRecord.create(userId, candidates.get(i).type(),
            texts.get(i), hash, sessionId));
    }

    // Phase 5: 记忆 UPDATE 判定（语义相似度 > 0.9 → UPDATE 而非 ADD）
    List<MemoryRecord> finalInserts = new ArrayList<>();
    for (int i = 0; i < toInsert.size(); i++) {
        MemoryRecord record = toInsert.get(i);
        float[] embedding = embeddings.get(i);
        
        // 在已有记忆中查找是否有高度相似的（cosine > 0.9）
        Optional<ScoredMemory> nearDuplicate = existingMemories.stream()
            .map(existing -> new ScoredMemory(existing.getId(), existing.getContent(),
                cosineSimilarity(embedding, vectorStore.getVector(existing.getId()))))
            .filter(sm -> sm.score() > 0.9)
            .max(Comparator.comparingDouble(ScoredMemory::score));
        
        if (nearDuplicate.isPresent()) {
            // UPDATE：新内容替换旧内容
            Long existingId = nearDuplicate.get().id();
            String oldContent = nearDuplicate.get().content();
            memoryRepository.updateContent(existingId, record.getContent(), md5(record.getContent()));
            vectorStore.update(existingId, embedding, record.getContent());
            memoryRepository.insertHistory(existingId, oldContent, record.getContent(), "UPDATE", sessionId);
        } else {
            finalInserts.add(record);
        }
    }

    // Phase 6: 词形还原（为 BM25 关键词搜索准备）
    // 中文使用 ngram 分词，存储分词结果供后续 FULLTEXT 检索
    for (MemoryRecord record : finalInserts) {
        record.setContentTokenized(tokenizeForSearch(record.getContent()));
    }

    // Phase 7: 批量持久化（MySQL + 向量库 + history）
    for (int i = 0; i < finalInserts.size(); i++) {
        MemoryRecord record = finalInserts.get(i);
        try {
            memoryRepository.insert(record);
            vectorStore.insert(record.getId(), userId, embeddings.get(i),
                record.getContent(), record.getContentHash());
            memoryRepository.insertHistory(record.getId(), null,
                record.getContent(), "ADD", sessionId);
        } catch (DuplicateKeyException e) {
            log.debug("跳过重复记忆: hash={}", record.getContentHash());
        }
    }

    // Phase 8: 保存原始消息到 chat_message（已在 ChatService 层完成，此处跳过）
}
```

### 3.3 混合检索实现（完整版，语义 + BM25 关键词 + 时间衰减 + 重要性）

```java
/**
 * 对应 Mem0._search_vector_store()，完整混合检索 pipeline
 */
public List<MemoryItem> search(Long userId, String query, int topK, double threshold) {
    // Step 1: 查询预处理
    String queryTokenized = tokenizeForSearch(query);  // 中文分词（ngram）

    // Step 2: embed 查询
    float[] queryEmbedding = embeddingClient.embed(query);

    // Step 3: 语义搜索（over-fetch 4x）
    int overFetch = Math.max(topK * 4, 60);
    List<ScoredMemory> semanticResults = vectorStore.search(userId, queryEmbedding, overFetch);

    // Step 4: BM25 关键词搜索（MySQL FULLTEXT ngram）
    Map<Long, Double> bm25Scores = new HashMap<>();
    List<MemoryRecordPO> keywordResults = memoryRecordDao.fulltextSearch(userId, queryTokenized, overFetch);
    if (!keywordResults.isEmpty()) {
        double maxRawScore = keywordResults.stream()
            .mapToDouble(MemoryRecordPO::getMatchScore).max().orElse(1.0);
        for (MemoryRecordPO r : keywordResults) {
            // BM25 归一化：sigmoid 映射到 [0, 1]
            double normalized = sigmoidNormalize(r.getMatchScore(), maxRawScore);
            bm25Scores.put(r.getId(), normalized);
        }
    }

    // Step 5: 评分融合（semantic + bm25 + recency + importance）
    boolean hasBm25 = !bm25Scores.isEmpty();
    double maxPossible = 1.0 + (hasBm25 ? 1.0 : 0.0) + 0.3 + 0.2;  // 语义1.0 + BM25 1.0 + 时间0.3 + 重要性0.2

    List<ScoredMemory> scored = new ArrayList<>();
    for (ScoredMemory r : semanticResults) {
        double semanticScore = r.score();

        // 门控：语义分低于阈值直接排除
        if (semanticScore < threshold) continue;

        double bm25Score = bm25Scores.getOrDefault(r.id(), 0.0);
        double recencyBoost = computeRecencyBoost(r.lastAccessedAt());      // 0 ~ 0.3
        double importanceBoost = r.importance() * 0.2;                      // 0 ~ 0.2

        double combined = (semanticScore + bm25Score + recencyBoost + importanceBoost) / maxPossible;
        combined = Math.min(combined, 1.0);

        scored.add(new ScoredMemory(r.id(), r.content(), combined));
    }

    // Step 6: 合并 BM25 独有结果（语义搜索没覆盖到的）
    Set<Long> semanticIds = semanticResults.stream().map(ScoredMemory::id).collect(Collectors.toSet());
    for (Map.Entry<Long, Double> entry : bm25Scores.entrySet()) {
        if (!semanticIds.contains(entry.getKey())) {
            // BM25 命中但语义未命中的记忆，仅在 BM25 分 > 0.5 时纳入
            if (entry.getValue() > 0.5) {
                MemoryRecordPO po = memoryRecordDao.selectById(entry.getKey());
                double combined = (0.0 + entry.getValue() + 0.0 + 0.0) / maxPossible;
                scored.add(new ScoredMemory(po.getId(), po.getContent(), combined));
            }
        }
    }

    // Step 7: 排序截断 + 更新 access_count
    scored.sort((a, b) -> Double.compare(b.score(), a.score()));
    List<MemoryItem> results = scored.subList(0, Math.min(topK, scored.size())).stream()
        .map(this::toMemoryItem).toList();

    // 异步更新命中记忆的 access_count 和 last_accessed_at
    List<Long> hitIds = results.stream().map(MemoryItem::getId).toList();
    CompletableFuture.runAsync(() -> memoryRepository.batchUpdateAccessInfo(hitIds));

    return results;
}

/**
 * BM25 归一化：sigmoid 函数
 */
private double sigmoidNormalize(double rawScore, double maxScore) {
    double midpoint = maxScore * 0.5;
    double steepness = 0.6;
    return 1.0 / (1.0 + Math.exp(-steepness * (rawScore - midpoint)));
}

/**
 * 时间衰减：最近访问的记忆获得更高 boost
 */
private double computeRecencyBoost(LocalDateTime lastAccessedAt) {
    if (lastAccessedAt == null) return 0.0;
    long daysSince = ChronoUnit.DAYS.between(lastAccessedAt, LocalDateTime.now());
    // 指数衰减：0天=0.3, 7天=0.15, 30天≈0.05, 90天≈0
    return 0.3 * Math.exp(-0.05 * daysSince);
}
```

### 3.4 MySQL FULLTEXT 支持（为 BM25 混合检索准备）

```sql
-- memory_record 表新增分词字段和全文索引
ALTER TABLE `memory_record` 
  ADD COLUMN `content_tokenized` varchar(1024) DEFAULT NULL COMMENT '分词后内容（供FULLTEXT使用）',
  ADD FULLTEXT INDEX `ft_content` (`content`) WITH PARSER ngram,
  ADD FULLTEXT INDEX `ft_content_tokenized` (`content_tokenized`) WITH PARSER ngram;

-- BM25 关键词检索 SQL
SELECT id, content, MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) AS match_score
FROM memory_record
WHERE user_id = ? AND is_deleted = 0
  AND MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE)
ORDER BY match_score DESC
LIMIT ?;
```

> MySQL 8 的 ngram parser 默认 token_size=2，对中文开箱即用，无需外部分词器。

### 3.5 记忆抽取 Prompt 设计

基于 Mem0 的 `FACT_RETRIEVAL_PROMPT` 和 `ADDITIVE_EXTRACTION_PROMPT` 改写：

```
你是一个记忆抽取器。从以下对话中提取值得长期记住的信息，以 JSON 格式返回。

【记忆类型】
- fact: 用户的客观事实（技术栈、项目、角色、工作内容）
- preference: 写作偏好（风格要求、格式偏好、字数控制、技术选型倾向）
- knowledge: 技术知识点（对话中讨论的值得记住的技术结论、最佳实践）
- event: 事件记录（写过的文章主题、做过的技术决策、时间节点）

【已有记忆 — 请勿重复提取】
{existing_memories}

【最近上下文】
{last_messages}

【新消息 — 需要从中抽取记忆】
{new_messages}

【规则】
1. 每条记忆用中文概括，50 字以内
2. 已有记忆中的信息不要重复提取（包括语义等价的信息）
3. 只提取长期有价值的信息，忽略一次性指令、临时的格式要求和闲聊内容
4. 每条记忆需标记 "attributed_to"：消息来自用户写"user"，来自 AI 写"agent"
5. 如果新记忆与已有记忆存在矛盾（如用户说技术栈变了），仍以新记忆为准

【Few-shot Examples】
Input: 帮我写一篇 JVM 调优的文章
Output: {"memory":[{"text":"撰写 JVM 调优技术文章","type":"event","attributed_to":"user"}]}

Input: 我喜欢用表格对比方案，标题要带数字编号
Output: {"memory":[{"text":"偏好 Markdown 表格对比，标题带数字编号","type":"preference","attributed_to":"user"}]}

Input: 我们项目用 Java 17 + Spring Boot 3.4，数据库是 MySQL 8.0
Output: {"memory":[{"text":"技术栈 Java 17 + Spring Boot 3.4 + MySQL 8.0","type":"fact","attributed_to":"user"}]}

Input: 你好 / 谢谢 / 可以
Output: {"memory":[]}

如无值得长期记忆的内容，返回: {"memory":[]}
```

### 3.6 与 Mem0 的对齐清单（完整实现）

| Mem0 特性 | 本项目实现 | 实现阶段 |
|-----------|----------|:--------:|
| V3 分阶段 Pipeline（8 阶段） | ✅ 完整实现（Phase 0-8） | v1 |
| 三层去重（LLM + Hash + DB） | ✅ 完整实现 | v1 |
| 记忆 UPDATE 判定（cosine > 0.9） | ✅ Phase 5 中实现 | v1 |
| Over-fetch 4x + 内存精排 | ✅ search 方法中实现 | v1 |
| BM25 关键词搜索 | ✅ MySQL FULLTEXT ngram | v1 |
| 评分融合（semantic + bm25 + recency + importance） | ✅ 加权归一化 | v1 |
| 门控机制（threshold gate） | ✅ 语义分低于阈值直接排除 | v1 |
| history 变更审计 | ✅ ADD/UPDATE/DELETE 全记录 | v1 |
| 启动 warm-up（向量库重载） | ✅ ApplicationReadyEvent | v1 |
| spaCy 实体提取 + entity_store | 用 HanLP 中文 NER 替代 | v2 |
| 实体 Boost（entity → memory 关联加分）| 基于 NER 实体索引表实现 | v2 |
| Reranker（Cohere/BGE） | BGE-reranker-v2-m3 | v2 |
| 独立 messages 表 | chat_message 表 | 前置改造 |
| 24 种向量库适配 | 接口抽象 + 2 种实现（内存 / pgvector） | v1 内存, v2 pgvector |
| procedural_memory 类型 | 跳过（写作场景不需要） | — |
| vision 消息处理 | v3 多模态记忆中实现 | v3 |
| Memory Graph（记忆间关系） | MySQL 邻接表 + 二阶检索 | v3 |

---

## 四、模块结构（遵循项目 DDD 惯例）

> ⚠️ 修正：原方案将 adapter 接口放在 `service/memory/adapter/`，不符合项目既有惯例。
> 项目中 `IAiTaskRepository` 在 `domain/agent/adapter/repository/`，实现在 `infrastructure/adapter/repository/`。
> 以下结构对齐项目 DDD 规范。

```
# ===== 领域层（domain 模块）=====
cn.sutone.ai.domain.agent.adapter.repository/
├── IMemoryRepository.java          # MySQL CRUD + history 接口
└── IMemoryVectorStore.java         # 向量存储接口（可插拔）

cn.sutone.ai.domain.agent.model.entity/
└── MemoryRecordEntity.java         # 记忆领域实体

cn.sutone.ai.domain.agent.model.valobj/
└── MemoryTypeVO.java               # fact / preference / knowledge / event

cn.sutone.ai.domain.agent.service.memory/
├── MemoryManager.java              # Pipeline 编排门面：add() + search() + CRUD
├── MemoryExtractor.java            # Phase 2: LLM 抽取 prompt + 防御性 JSON 解析
└── MemoryRetriever.java            # 混合检索 + 时间衰减 + context 格式化

# ===== 基础设施层（infrastructure 模块）=====
cn.sutone.ai.infrastructure.dao/
├── IMemoryRecordDao.java           # MyBatis Mapper
├── IMemoryHistoryDao.java          # MyBatis Mapper
└── po/
    ├── MemoryRecordPO.java
    └── MemoryHistoryPO.java

cn.sutone.ai.infrastructure.adapter.repository/
├── MemoryRepository.java           # IMemoryRepository 的 MySQL 实现
└── SimpleMemoryVectorStore.java    # IMemoryVectorStore 的内存实现（含启动 warm-up）

cn.sutone.ai.infrastructure.gateway/
└── MemoryEmbeddingClient.java      # embeddings API 客户端（复用 OpenAiApi 配置）

# ===== 触发器层（trigger 模块）=====
cn.sutone.ai.trigger.http/
└── MemoryController.java           # REST API

# ===== API 层 =====
cn.sutone.ai.api/
├── IMemoryService.java
└── dto/memory/*.java

# ===== 资源文件 =====
resources/agent/prompts/
└── memory-extraction-prompt.txt    # 抽取 prompt 模板（不做成 YAML Agent）
```

**设计决策说明：**
- 记忆抽取 prompt 不做成 YAML Agent，因为它是单轮 LLM 调用，无工具、无多步骤、无 session
- 记忆暂不设为独立限界上下文，当前仅服务 agent 写作，未来多 BC 共用时再抽离

---

## 五、数据库设计

```sql
-- ============================================================
-- Agent 记忆系统 DDL
-- ============================================================

-- 记忆主表
CREATE TABLE `memory_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记忆ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户',
  `type` varchar(32) NOT NULL COMMENT '类型: fact/preference/knowledge/event',
  `content` varchar(512) NOT NULL COMMENT '记忆内容',
  `content_hash` varchar(64) NOT NULL COMMENT 'MD5(content)，去重第一道',
  `content_tokenized` varchar(1024) DEFAULT NULL COMMENT '分词后内容（供BM25 FULLTEXT检索）',
  `source_session_id` varchar(64) DEFAULT NULL COMMENT '来源会话ID',
  `importance` double NOT NULL DEFAULT '0.5' COMMENT '重要性权重 0-1',
  `access_count` int(11) NOT NULL DEFAULT '0' COMMENT '被检索命中次数',
  `last_accessed_at` datetime DEFAULT NULL COMMENT '最近被检索时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_hash` (`user_id`, `content_hash`),
  KEY `idx_user_type` (`user_id`, `type`),
  FULLTEXT KEY `ft_content` (`content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent长期记忆表';

-- 记忆变更历史（对应 Mem0 SQLiteManager.history 表）
CREATE TABLE `memory_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `memory_id` bigint(20) NOT NULL COMMENT '关联 memory_record.id',
  `session_id` varchar(64) NOT NULL COMMENT '来源会话',
  `old_content` text COMMENT '变更前内容',
  `new_content` text COMMENT '变更后内容',
  `event` varchar(16) NOT NULL COMMENT 'ADD / UPDATE / DELETE',
  `role` varchar(16) DEFAULT NULL COMMENT 'user / agent',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_memory_id` (`memory_id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆变更历史表';
```

---

## 六、REST API 设计

```
GET    /api/v1/memory/search?q=JVM调优&n=5    → 语义搜索记忆
GET    /api/v1/memory/list?page=1&pageSize=20  → 分页记忆列表
GET    /api/v1/memory/{id}                     → 单条记忆详情
DELETE /api/v1/memory/{id}                     → 删除记忆
POST   /api/v1/memory/refresh?sessionId=xxx    → 手动触发重新抽取（管理接口）
```

---

## 七、接入写作 Agent

### 7.1 写入：写作完成后异步抽取

> ⚠️ 修正：不使用 `CompletableFuture.runAsync()`，改用 Spring `@Async` 注解 + 专用线程池。
> 原因：`CompletableFuture.runAsync()` 使用 ForkJoinPool.commonPool()，无法控制线程数、无法传播 Spring 上下文。

```java
// MemoryManager.java
@Async("memoryExecutor")
public void addAsync(Long userId, String sessionId, String userPrompt, String aiResponse) {
    try {
        List<Map<String, String>> messages = List.of(
            Map.of("role", "user", "content", userPrompt),
            Map.of("role", "assistant", "content", aiResponse)
        );
        this.add(userId, Long.parseLong(WRITING_AGENT_ID), sessionId, messages);
    } catch (Exception e) {
        log.warn("记忆抽取失败 userId={} sessionId={}", userId, sessionId, e);
    }
}
```

```java
// AiWritingService.generateStream() 中，markSuccess 之后调用：
memoryManager.addAsync(userId, sessionId, task.getPromptPayload(), responseBuilder.toString());
```

```java
// ThreadPoolConfig.java 中新增专用线程池 Bean：
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

### 7.2 读取：写作前注入记忆上下文

```java
// AiWritingService.buildPrompt() 中，prompt 模板前新增：

String memoryContext = memoryRetriever.retrieveFormattedContext(userId, draft.getContentMd(), 5);

// 非空时拼接到 prompt 开头：
String memoryPrefix = memoryContext.isBlank() ? "" : "【用户记忆上下文】\n" + memoryContext + "\n\n";
// 然后在各 taskType case 中 prepend memoryPrefix
```

---

## 八、关键技术风险与防御措施

### 8.1 内存向量库重启丢失（严重）

**问题**：`SimpleMemoryVectorStore` 用 ConcurrentHashMap 存 embedding，重启后 MySQL 有记忆但无法搜索。

**解决方案：ApplicationReadyEvent warm-up**

```java
@Component
public class SimpleMemoryVectorStore implements IMemoryVectorStore {

    private final ConcurrentHashMap<Long, VectorEntry> store = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        List<MemoryRecordPO> all = memoryRecordDao.selectAllActive();
        for (List<MemoryRecordPO> batch : Lists.partition(all, 50)) {
            List<String> texts = batch.stream().map(MemoryRecordPO::getContent).toList();
            List<float[]> embeddings = embeddingClient.embedBatch(texts);
            for (int i = 0; i < batch.size(); i++) {
                store.put(batch.get(i).getId(), new VectorEntry(
                    batch.get(i).getUserId(), embeddings.get(i), texts.get(i),
                    batch.get(i).getContentHash()));
            }
        }
        log.info("记忆向量库 warm-up 完成，加载 {} 条记忆", all.size());
    }
}
```

**规模评估**：DeepSeek embedding 输出 1024 维，每条 4KB。5000 条 = 20MB，1G 堆内存完全可承受。启动耗时约 3-5 秒。

### 8.2 LLM 抽取 JSON 解析失败

**问题**：DeepSeek 偶尔在 JSON 前后加 markdown code fence 或解释性文字。

**防御性解析策略**：

```java
private List<MemoryCandidate> parseExtractionResponse(String llmResponse) {
    try {
        // 1. 去除 markdown code fence
        String cleaned = llmResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        // 2. 正则提取最外层 JSON 对象
        Matcher matcher = Pattern.compile("\\{[\\s\\S]*\\}").matcher(cleaned);
        if (!matcher.find()) return Collections.emptyList();
        String jsonStr = matcher.group();
        // 3. 解析
        JSONObject obj = JSON.parseObject(jsonStr);
        JSONArray memories = obj.getJSONArray("memory");
        if (memories == null || memories.isEmpty()) return Collections.emptyList();
        // 4. 逐条验证
        List<MemoryCandidate> result = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            JSONObject m = memories.getJSONObject(i);
            String text = m.getString("text");
            String type = m.getString("type");
            if (text == null || text.length() > 200) continue;
            if (!MemoryTypeVO.isValid(type)) continue;
            result.add(new MemoryCandidate(text, type, m.getString("attributed_to")));
        }
        return result;
    } catch (Exception e) {
        log.warn("记忆抽取 JSON 解析失败，原始响应: {}", llmResponse, e);
        return Collections.emptyList();
    }
}
```

### 8.3 并发写入重复记忆

**问题**：两个写作任务同时完成 → 都读到相同的已有记忆 → 各自抽取出相同内容 → 同时 insert。

**解决方案**：MySQL 唯一约束 `uk_user_hash(user_id, content_hash)` 兜底 + 代码捕获异常：

```java
try {
    memoryRecordDao.insert(po);
} catch (DuplicateKeyException e) {
    log.debug("跳过重复记忆: hash={}", po.getContentHash());
    continue;
}
```

### 8.4 @Async 线程池未正确绑定

**问题**：项目现有 `ThreadPoolConfig` 定义的是 `ThreadPoolExecutor` bean，Spring `@Async` 默认不会使用它。

**解决方案**：新增 `memoryExecutor` bean（见 7.1 节），在 MemoryManager 中使用 `@Async("memoryExecutor")`。

---

## 九、执行步骤与工期

| 步骤 | 内容 | 预估 |
|------|------|:---:|
| 1 | DDL（memory_record + memory_history + chat_message + FULLTEXT 索引） + PO + DAO | 1 天 |
| 2 | Domain 层实体 + 接口（MemoryRecordEntity、MemoryTypeVO、IMemoryRepository、IMemoryVectorStore） | 0.5 天 |
| 3 | MemoryRepository（MySQL CRUD + history + FULLTEXT 查询）+ SimpleMemoryVectorStore（含 warm-up） | 1.5 天 |
| 4 | MemoryEmbeddingClient（复用 OpenAiApi embeddings 接口，支持 single + batch） | 0.5 天 |
| 5 | MemoryExtractor（prompt 模板 + LLM 调用 + 防御性 JSON 解析 + UPDATE 判定逻辑） | 1.5 天 |
| 6 | MemoryRetriever（混合检索：语义 + BM25 + 评分融合 + 门控 + over-fetch + access_count 更新） | 1.5 天 |
| 7 | MemoryManager（完整 V3 Pipeline 8 阶段 + @Async + CRUD） | 1 天 |
| 8 | MemoryController + REST API（search / list / detail / delete / update） | 0.5 天 |
| 9 | ChatService 改造（对话持久化到 chat_message 表） | 1 天 |
| 10 | 写作 Agent 多轮对话改造（新 YAML + WritingChatController） | 1 天 |
| 11 | 接入记忆系统（对话开始注入长期记忆 + 保存时触发抽取 + memoryExecutor Bean） | 1 天 |
| 12 | 端到端验证（混合检索 + 去重 + UPDATE + warm-up + 多轮对话） | 1.5 天 |
| **合计** | | **13 天** |

---

## 十、验证清单

- [ ] 多轮对话正常工作：用户可以和写作 Agent 来回对话
- [ ] chat_message 表正确记录每条对话消息
- [ ] 用户保存文章后，自动触发记忆抽取
- [ ] `GET /api/v1/memory/list` 能看到自动抽取的记忆（包含 type/content/hash）
- [ ] 相同内容不产生重复记忆（三层去重验证）
- [ ] 语义近似内容走 UPDATE 而非 ADD（cosine > 0.9 验证）
- [ ] `GET /api/v1/memory/search?q=JVM` 混合检索返回结果（语义 + BM25 融合）
- [ ] BM25 能命中精确关键词（如型号"RTX 4090"语义搜索可能漏但关键词能命中）
- [ ] 门控生效：语义分低于阈值的记忆不出现在结果中
- [ ] 删除一条记忆后，再次搜索不出现
- [ ] 新对话开始时 prompt 中包含格式化的长期记忆上下文
- [ ] 重启服务后，记忆不丢失（MySQL 持久化验证）
- [ ] 重启后 warm-up 日志输出加载条数，搜索功能正常
- [ ] `memory_history` 表记录每次 ADD/UPDATE/DELETE 操作
- [ ] access_count 和 last_accessed_at 在检索命中后自动更新

---

## 十一、面试话术

> 我的 Agent 记忆系统完整参考了 Mem0 v2 的核心设计，在 Java 生态中做了等价实现。
>
> **写入侧**实现了 V3 分阶段批处理 Pipeline（8 阶段）：从 chat_message 表取最近上下文（Phase 0），用已有记忆检索结果传给 LLM 做语义去重（Phase 1-2），抽取结果走 MD5 hash 精确去重（Phase 4），高相似度记忆走 UPDATE 而非重复 ADD（Phase 5），最后批量 embedding + 词形还原 + 批量持久化（Phase 3+6+7）。去重一共三层：LLM 语义感知 → MD5 精确匹配 → MySQL 唯一约束兜底。
>
> **检索侧**实现了完整的混合检索：语义搜索 over-fetch 4 倍候选集 + MySQL FULLTEXT ngram 做 BM25 关键词搜索 + 时间衰减 + 重要性加权，四路信号通过归一化后加权融合（`combined = (semantic + bm25 + recency + importance) / max_possible`），门控机制确保语义分低于阈值的不会因为关键词匹配而被推到高位。
>
> **架构上**，embedding 复用项目已有的 DeepSeek API，向量存储用接口抽象（内存实现 / pgvector 可切换）。对话消息持久化到 chat_message 表，记忆变更全程 history 审计。内存向量库通过 ApplicationReadyEvent 启动时 warm-up。
>
> **和 Mem0 完全对齐的部分**：V3 Pipeline、三层去重、UPDATE 判定、over-fetch、评分融合、门控、BM25 混合检索、history 审计。**后续补齐的**：中文 NER 实体链接 + 实体 Boost、Reranker 精排、pgvector 持久化向量库。

---

## 十二、版本演进路线图（v1 / v2 / v3）

> v1 是一次性做完的完善记忆系统（含多轮对话改造）。v2/v3 是后续增强方向。

### V1：核心记忆系统（当前版本）

**目标**：实现完善的记忆系统，一次性对齐 Mem0 核心能力（混合检索 + 完整 Pipeline + 三层去重 + UPDATE 判定 + BM25）。

**包含多轮对话改造**（chat_message 表 + 对话持久化 + 新 Agent YAML 作为 v1 的一部分直接实现）。

**范围**：
- 多轮对话改造（chat_message 表 + ChatService 持久化 + WritingChatController + 新 Agent YAML）
- V3 完整 Pipeline（8 阶段：上下文收集 → 已有记忆检索 → LLM 抽取 → 批量 embedding → Hash 去重 → UPDATE 判定 → 词形还原 → 批量持久化）
- 三层去重（LLM 语义 → MD5 精确 → MySQL UK 兜底）
- 记忆 UPDATE 能力（cosine > 0.9 时走更新而非新增）
- **混合检索**（语义搜索 + BM25 关键词搜索 + 时间衰减 + 重要性加权，over-fetch 4x）
- MySQL FULLTEXT ngram 索引（BM25 关键词搜索基础设施）
- 评分融合 + 门控机制（semantic + bm25 + recency + importance / max_possible）
- BM25 sigmoid 归一化
- 内存向量库（ConcurrentHashMap + cosine + 启动 warm-up）
- 接入多轮对话写作 Agent（从 chat_message 取最近 10 条上下文、用户保存文章时触发抽取、对话开始时注入长期记忆）
- REST API（search / list / detail / delete / update）
- access_count + last_accessed_at 自动更新
- memory_history 完整审计（ADD / UPDATE / DELETE）

**人天**：13 天（见第九章详细步骤）

**技术栈**：MySQL 持久化 + FULLTEXT ngram + 内存向量库 + DeepSeek Embedding API + @Async 异步抽取

---

### V2：实体链接 + Reranker + 持久化向量库

**目标**：补全实体链接和 Reranker，迁移到持久化向量库，解决重启问题。

**范围**：

| 功能 | 说明 | 预估 |
|------|------|:---:|
| pgvector 迁移 | memory_record 表新增 `embedding vector(1024)` 列，IMemoryVectorStore 新增 PgVectorStore 实现，通过配置切换 | 2 天 |
| 中文 NER 实体链接 | 集成 HanLP，从记忆中提取实体（技术栈、人名、项目名），建立 entity → memory 索引表 | 2 天 |
| 实体 Boost（对齐 Mem0） | 查询时提取实体 → entity_store 搜索 → 关联记忆加 boost 分（最高 0.5）→ 融合到评分公式 | 1 天 |
| Reranker 精排 | 集成 BGE-reranker-v2-m3，search 最终阶段对 top-20 精排到 top-5 | 1.5 天 |
| Redis 热记忆缓存 | `memory:user:{userId}:top20` TTL 5min，写入/删除时失效 | 0.5 天 |
| 多 Agent 接入 | PPT Agent、Draw.io Agent 接入记忆系统（同一套 pipeline，不同 agentId） | 1 天 |
| 记忆重要性自适应 | importance 衰减公式：`importance *= 0.95^(daysSinceLastAccess)` + 每日定时任务 | 0.5 天 |
| 重启 Session 恢复 | 重启后从 chat_message 表加载历史消息，拼接到当前对话 prompt 中恢复上下文 | 1 天 |
| 记忆抽取增强触发 | 补充会话超时（30min）触发 + 每 10 轮对话自动触发 | 0.5 天 |
| **合计** | | **10 天** |

**技术决策**：
- pgvector vs Milvus/Qdrant：选 pgvector，运维成本最低。如果不想引入 PostgreSQL，可在 MySQL `memory_record` 表新增 `embedding_json JSON` 列存储向量
- Reranker 选型：BGE-reranker-v2-m3（开源、中文强），通过 HTTP 微服务部署
- 实体链接：HanLP 4.x Java 原生支持，不需要外部 Python 服务
- 不用 Spring AI 的 VectorStore：它接管表结构、Document 模型不匹配记忆实体

**验证清单**：
- [ ] pgvector 切换后，search 性能 < 50ms（1000 条记忆）
- [ ] 双路召回（语义 + 关键词）比纯语义提升 recall@10 至少 15%
- [ ] Redis 缓存命中率 > 70%（在连续写作场景下）
- [ ] PPT/Draw.io 写作后能看到对应 agentId 的记忆
- [ ] 重启服务后，继续之前的对话，Agent 仍能理解上下文
- [ ] 超时/定轮触发正常工作，不遗漏长对话中的记忆
- [ ] 实体 Boost 生效：查询包含已知实体时，相关记忆排名提升
- [ ] Reranker 后 top-5 精度比无 Reranker 提升明显

---

### V3：记忆图谱 + 多模态 + 生产化

**目标**：构建记忆间的关联关系（知识图谱），支持多模态记忆，达到生产级可靠性。

**范围**：

| 功能 | 说明 | 预估 |
|------|------|:---:|
| 记忆图谱（Memory Graph） | 基于 Mem0 的 graph memory 设计，用 MySQL 邻接表存储记忆间关系（references、contradicts、supersedes） | 3 天 |
| 中文 NER 实体链接 | 集成 HanLP 或 LAC，从记忆中提取实体（技术栈、人名、项目名），建立实体 → 记忆索引 | 2 天 |
| Reranker 精排 | 集成 BGE-reranker 或 Cohere rerank API，search 最终阶段对 top-20 精排到 top-5 | 1.5 天 |
| 多模态记忆 | 支持图片记忆（Draw.io 产出、PPT 截图），存储图片描述文本 + 图片 URL | 2 天 |
| 记忆衰减与归档 | 超过 90 天未访问 + importance < 0.2 的记忆自动归档（软删除），每日定时任务 | 1 天 |
| 记忆推理 | 基于图谱的二阶检索：查到记忆 A → 沿关系边找到关联记忆 B/C → 一并注入 prompt | 2 天 |
| 记忆导入/导出 | 支持 JSON 格式批量导入导出，方便用户迁移或备份 | 1 天 |
| 前端记忆管理页面 | 可视化记忆列表、搜索、编辑、删除、图谱可视化 | 3 天 |
| 生产化加固 | 限流（单用户每分钟最多 5 次 add）、embedding 降级（API 超时走缓存）、监控告警 | 1.5 天 |
| 自定义 SessionService | 替换 InMemorySessionService，将 ADK Session 持久化到 MySQL，彻底解决重启丢失问题 | 2 天 |
| **合计** | | **19 天** |

**技术决策**：
- 图谱存储：不引入 Neo4j，用 MySQL 邻接表 `memory_relation(from_id, to_id, relation_type)` + 递归 CTE 查询。记忆量级（万级）不需要图数据库
- Reranker 选型：优先 BGE-reranker-v2-m3（开源、中文强），通过 HTTP 微服务部署，search pipeline 最后一步调用
- 多模态：不存储原始图片 embedding，只存 LLM 生成的图片描述文本的 embedding

---

### 完整实施总览

```
v1 完善记忆系统（13 天）                增强一（10 天）                   增强二（15.5 天）
───────────────────────────────    ─────────────────────────────    ─────────────────────────────
多轮对话改造 + chat_message        pgvector 持久化向量库             记忆图谱 + 二阶检索
完整 V3 Pipeline（8 阶段）          中文 NER 实体链接                自定义 SessionService
混合检索（语义 + BM25 融合）        实体 Boost                       多模态记忆
三层去重 + UPDATE 判定              Reranker 精排(BGE)               记忆衰减归档
评分融合 + 门控 + over-fetch 4x    Redis 热缓存                     记忆导入/导出
MySQL FULLTEXT ngram               多 Agent 接入                    前端管理页面 + 图谱可视化
内存向量库 + warm-up               重启 Session 恢复                生产化加固 + 监控
REST API + history 审计            记忆抽取增强触发                  
                                   重要性自适应
```

**总工期**：v1(13d) + 增强一(10d) + 增强二(15.5d) = **38.5 个工作日**

**关键节点**：
- v1 完成（13 天）：完善的记忆系统已可用，对齐 Mem0 核心能力（混合检索 + 去重 + UPDATE + Pipeline），面试展示有深度
- 增强一完成（+10 天）：对齐 Mem0 全部检索能力（实体 + Reranker + pgvector），生产可用
- 增强二完成（+15.5 天）：超越 Mem0 开源版（图谱 + 多模态 + 前端），可作为独立产品

---

## 十三、多轮对话改造（v1 的一部分）

> 以下改造已纳入 v1 的执行步骤（第九章步骤 9-10），此处保留详细设计参考。

### 13.1 为什么需要这个改造

当前写作模式是「一次性任务」：用户提交参数 → Agent 一次性返回完整文章 → 结束。用户无法追问、修改、补充。

改为多轮对话后：
- 用户可以和写作 Agent 持续交流，逐步完善文章
- 记忆系统需要从多轮对话中提取记忆（需要对话历史作为上下文）
- 用户体验更接近「和 AI 编辑协作」而非「给 AI 下单」

### 13.2 现有架构分析

当前项目已经具备多轮对话的基础能力：

```
AgentServiceController.java:
  POST /api/v1/create_session  → 创建会话
  POST /api/v1/chat            → 同步对话
  POST /api/v1/chat_stream     → 流式对话（已支持 sessionId 复用）

ChatService.java:
  - InMemoryRunner + InMemorySessionService
  - 同一 sessionId 内，Google ADK 自动维护对话历史
  - 已有 userSessions ConcurrentHashMap 缓存 userId → sessionId 映射
```

**问题**：对话历史只存在 ADK 的内存中，重启丢失，且记忆系统无法访问这些对话记录。

### 13.3 改造方案

#### 方案概述

```
┌─────────────────────────────────────────────────────────────┐
│                  多轮对话写作模式                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  用户 ──▶ POST /api/v1/writing/chat_stream                  │
│           (sessionId + message)                             │
│                │                                            │
│                ▼                                            │
│  ┌──────────────────────┐   ┌────────────────────────┐     │
│  │ ChatService          │   │ chat_message 表         │     │
│  │ (ADK InMemoryRunner) │──▶│ 持久化对话历史          │     │
│  │ 维护会话内上下文      │   │ 供记忆系统读取          │     │
│  └──────────────────────┘   └────────────────────────┘     │
│                │                          │                  │
│                ▼                          ▼                  │
│  AI 流式响应返回给用户         记忆系统定期从表中取最近 N 条    │
│                                作为 LLM 抽取的上下文         │
└─────────────────────────────────────────────────────────────┘
```

#### 新增 DDL：对话消息表

```sql
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `agent_id` varchar(32) NOT NULL COMMENT '智能体ID',
  `role` varchar(16) NOT NULL COMMENT 'user / assistant',
  `content` text NOT NULL COMMENT '消息内容',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_time` (`session_id`, `create_time`),
  KEY `idx_user_agent` (`user_id`, `agent_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息持久化表';
```

#### 写入对话记录的时机

在 `ChatService.handleMessageStream()` 中，每次用户发消息和 AI 回复完成后，异步写入 `chat_message` 表：

```java
// ChatService 改造
public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
    // 1. 持久化用户消息
    chatMessageRepository.save(userId, sessionId, agentId, "user", message);

    // 2. 正常调用 ADK runner
    InMemoryRunner runner = aiAgentRegisterVO.getRunner();
    Content userMsg = Content.fromParts(Part.fromText(message));
    Flowable<Event> events = runner.runAsync(userId, sessionId, userMsg);

    // 3. 在流结束后持久化 AI 回复（通过 doOnComplete 回调）
    StringBuilder aiResponse = new StringBuilder();
    return events.doOnNext(event -> {
        String content = event.stringifyContent();
        if (content != null && !content.isBlank()) {
            aiResponse.append(content);
        }
    }).doOnComplete(() -> {
        if (aiResponse.length() > 0) {
            chatMessageRepository.save(userId, sessionId, agentId, "assistant", aiResponse.toString());
        }
    });
}
```

#### 记忆系统如何使用对话记录

```java
// MemoryManager.addAsync() 中，取最近 10 条对话作为上下文
public void add(Long userId, String sessionId, ...) {
    // 从 chat_message 表取该 session 最近 10 条消息
    List<ChatMessagePO> lastMessages = chatMessageDao.selectLastN(sessionId, 10);

    // 构建 prompt 时传入作为上下文
    String prompt = buildExtractionPrompt(existingMemories, newMessages, lastMessages);
    // ... 后续 pipeline 不变
}
```

#### 记忆抽取触发时机调整

| 策略 | 触发条件 | 适用场景 |
|------|---------|---------|
| 会话保存时 | 用户点击「保存文章」按钮 | 主要触发点 |
| 会话超时时 | 30 分钟无新消息 | 兜底，防遗漏 |
| 每 N 轮对话 | 累计 10 轮对话时自动触发 | 长对话场景 |

建议 v1 只实现「会话保存时触发」，v2 补充超时和定轮触发。

### 13.4 写作 Agent 的交互模式改造

#### 改造前（一次性任务）

```
前端 → POST /api/v1/ai-writing/submit-task (draftId, taskType, params)
     → POST /api/v1/ai-writing/stream/{taskId}
     → 一次性拿到完整文章
     → 结束
```

#### 改造后（多轮对话）

```
前端 → POST /api/v1/writing/create_session (agentId, userId, draftId)
     → 返回 sessionId
     
前端 → POST /api/v1/writing/chat_stream (sessionId, message)
     → 流式返回 AI 回复
     → 用户可以继续发消息...

前端 → POST /api/v1/writing/chat_stream (sessionId, message)
     → 流式返回 AI 回复
     → ...循环

前端 → POST /api/v1/writing/save (sessionId, draftId)
     → 保存最终内容到草稿 + 触发记忆抽取
```

#### 写作 Agent YAML 改造

当前 `agent-writing.yml` 是 sequential workflow（analyst → generator → reviewer 固定流程），需要改为**单 Agent 多轮对话模式**：

```yaml
# agent-writing.yml 改造后
ai:
  agent:
    config:
      tables:
        writingAgent:
          app-name: writingAgent
          agent:
            agent-id: 300002
            agent-name: AI技术写作助手
            agent-desc: 多轮对话式技术写作助手，支持逐步完善文章
          module:
            ai-api:
              base-url: ${SUTONE_WRITING_MODEL_BASE_URL:https://api.deepseek.com}
              api-key: ${SUTONE_WRITING_MODEL_API_KEY:YOUR_KEY}
              completions-path: ${SUTONE_WRITING_MODEL_COMPLETIONS_PATH:chat/completions}
              embeddings-path: ${SUTONE_WRITING_MODEL_EMBEDDINGS_PATH:embeddings}
            chat-model:
              model: ${SUTONE_WRITING_MODEL_NAME:deepseek-v4-pro}
            agents:
              - name: writing_assistant
                description: 多轮对话式技术写作助手
                instruction: |
                  你是一个高级技术写作助手。用户会和你多轮对话来逐步完善文章。
                  
                  【核心职责】
                  - 根据用户需求生成、修改、润色技术文章
                  - 记住当前对话中用户的所有指令和偏好
                  - 每次输出可直接使用的 Markdown 内容
                  
                  【输出格式规则】
                  （保持现有的格式硬规则...）
                  
            runner:
              agent-name: writing_assistant
```

#### 保留一次性模式作为快捷入口

改造不需要删除现有的一次性写作 API，可以保留作为「快速生成」入口：

```
多轮对话模式：适合需要反复打磨的文章
一次性任务模式：适合快速生成大纲、摘要、标题等简单任务
```

### 13.5 执行步骤与工期

| 步骤 | 内容 | 预估 |
|------|------|:---:|
| 1 | DDL chat_message 表 + PO + DAO | 0.5 天 |
| 2 | ChatService 改造：流式对话中持久化消息 | 1 天 |
| 3 | 新增 WritingChatController（多轮对话 API） | 1 天 |
| 4 | 写作 Agent YAML 改造（sequential → 单 Agent 多轮） | 0.5 天 |
| 5 | 前端对接：对话框 UI + 流式渲染 | 2 天 |
| 6 | 记忆系统接入点调整（从 chat_message 取上下文） | 0.5 天 |
| 7 | 端到端验证 | 1 天 |
| **合计** | | **6.5 天** |

### 13.6 与记忆系统的关系

多轮对话改造和记忆系统在 v1 中一次性完成，总计 13 天。

### 13.7 注意事项

1. **InMemorySession 仍有重启丢失问题**：Google ADK 的 session 在内存中，重启后新消息无法衔接旧对话的上下文。但 `chat_message` 表持久化了所有历史消息，重启后可以从表中恢复最近 N 条重新构建 prompt（v2 优化）。

2. **并发安全**：同一个 sessionId 只允许一个活跃的对话请求，前端需要做发送按钮防抖，后端可加 Redis 锁。

3. **消息存储量控制**：`chat_message` 表会持续增长，建议按 session 维度保留最近 100 条，超过的定期归档或清理。

---

## 十四、基础设施复用：记忆系统 → RAG 知识库

| 基础设施 | 记忆系统 | → RAG 复用 |
|---------|---------|-----------|
| MemoryEmbeddingClient | embedding 客户端 | 直接复用 |
| IMemoryVectorStore + 实现 | 向量存储接口 | 同一套接口 |
| 检索 + over-fetch + 精排模式 | 语义检索模式 | RAG 检索流程完全一致 |
| Extraction prompt 设计经验 | LLM 抽取模式 | RAG 不需要（文档已结构化） |
| chat_message 表 | 对话持久化 | RAG 对话也需要持久化 |

**记忆系统完成后，RAG 的工期从 7 天压缩到 3-4 天。**
