# 排行榜实现逻辑链路

> 2026-07-14

---

## 一、整体架构

```
写路径:  用户访问文章 → increaseViewCount(DB) + recordView(Redis ZSET)
读路径:  GET /leaderboard → getTopN(Redis ZSET 排名) → queryArticleDetail(取详情+viewCount)
展示:    按 Redis score 排序，展示 DB view_count
```

两条独立的计数链路：

| | 写入口 | 存储 | 作用 |
|---|---|---|---|
| DB `view_count` | `increaseViewCount()` → `UPDATE article_meta` | MySQL | **展示值**（总累计浏览量） |
| Redis ZSET score | `recordView()` → `ZINCRBY` | Redis | **排名依据**（当日浏览量） |

---

## 二、写链路：阅读记录

### 触发点

```
GET /api/v1/articles/{articleId}
  → ArticleController.queryArticleDetail()
  → ArticleDomainService.queryArticleDetail()
```

### 核心代码

```java
// ArticleDomainService.java:28-37
public ArticleEntity queryArticleDetail(Long articleId) {
    ArticleEntity articleEntity = articleCacheService.getArticleDetail(articleId);
    if (null == articleEntity) {
        throw new AppException("文章不存在");
    }
    articleRepository.increaseViewCount(articleId);  // ① DB +1
    patchDynamicFields(articleEntity, articleId);
    socialService.recordView(articleId);              // ② Redis ZINCRBY +1
    return articleEntity;
}
```

### ① `increaseViewCount` → DB

```java
// IArticleMetaDao.java
@Update("UPDATE article_meta SET view_count = view_count + 1 WHERE article_id = #{articleId}")
int increaseViewCount(@Param("articleId") Long articleId);
```

原子自增，保证并发安全。累计值，永久存储。

### ② `recordView` → Redis ZSET

```java
// SocialService.java:174-177
public void recordView(Long articleId) {
    String todayKey = buildKey("daily", LocalDate.now());
    RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(todayKey);
    set.addScore(articleId, 1);   // ZINCRBY key 1 articleId
}
```

```java
// SocialService.java:193-194
private String buildKey(String period, LocalDate date) {
    return "leaderboard:view:" + period + ":" + date.format(DF);
    // → leaderboard:view:daily:2026-07-14
}
```

**Redis 执行**：`ZINCRBY leaderboard:view:daily:2026-07-14 1 {articleId}`

ZSET 结构：
```
member   = articleId (Long)
score    = 当日被阅读次数（累加值）
key      = leaderboard:view:daily:{yyyy-MM-dd}   ← 按天分 key
```

**重要**：`leaderboard()` 排行榜接口用的是 `queryArticleDetailReadOnly()`，**不会**触发 `increaseViewCount` 和 `recordView`。只有用户真正点进文章详情页才会计数。这保证了排行榜通过文章详情页浏览才是有效的。

---

## 三、读链路：排行榜展示

### 请求入口

```
GET /api/v1/articles/leaderboard?period=daily&n=10
```

### 第1步：Redis 排名查询

```java
// SocialService.java:181-191
public Map<Long, Double> getTopN(String period, int n) {
    String key = buildKey(period, LocalDate.now());
                      // → leaderboard:view:daily:2026-07-14
    RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(key);
    Collection<Long> topIds = set.valueRangeReversed(0, n - 1);
                      // → ZREVRANGE key 0 9 WITHSCORES
    Map<Long, Double> result = new LinkedHashMap<>();
    for (Long articleId : topIds) {
        Double score = set.getScore(articleId);
        result.put(articleId, score != null ? score : 0.0);
    }
    return result;
    // → {123: 45.0, 456: 32.0, 789: 18.0, ...}
}
```

**Redis 执行**：`ZREVRANGE leaderboard:view:daily:2026-07-14 0 9 WITHSCORES`

返回当日浏览量最高的前 N 篇文章 ID，按 score 降序。

### 第2步：文章详情查询

```java
// ArticleController.java:141-155
Map<Long, Double> topN = socialService.getTopN(period, n);  // ← step 1
List<Map<String, Object>> result = new ArrayList<>();
for (Map.Entry<Long, Double> entry : topN.entrySet()) {
    try {
        // step 2: 加载文章实体（读缓存/DB，不触发计数）
        ArticleEntity article = articleDomainService.queryArticleDetailReadOnly(entry.getKey());

        result.add(Map.of(
            "articleId", article.getArticleId(),
            "title", article.getTitle(),
            "summary", article.getSummary() != null ? article.getSummary() : "",
            "viewCount", article.getMeta() != null ? article.getMeta().getViewCount() : 0
            // ↑ 展示的是 DB 中的总浏览量，不是 Redis score
        ));
    } catch (Exception ignored) {
        // 文章可能已被删除，跳过
    }
}
```

### 第3步：`queryArticleDetailReadOnly` 内部

```java
// ArticleDomainService.java:39-46
public ArticleEntity queryArticleDetailReadOnly(Long articleId) {
    ArticleEntity articleEntity = articleCacheService.getArticleDetail(articleId);
    //     → 查 Redis article:detail:{id} 缓存
    //     → miss → 查 DB article + article_meta → 写缓存 → 返回
    if (null == articleEntity) {
        throw new AppException("文章不存在");
    }
    patchDynamicFields(articleEntity, articleId);
    //   → setLikeCount()    ← Redis SCARD
    //   → setFavoriteCount()← Redis SCARD
    //   → setViewCount()    ← DB article_meta.view_count（最新累计值）
    return articleEntity;
}
```

> 注意：`queryArticleDetailReadOnly` 内部仍然会 patch viewCount，从 DB 读最新的累计浏览量。所以排行榜展示的 viewCount 是实时的。

---

## 四、数据流全图

```
                ┌──────────────┐
                │   用户访问文章  │
                │ GET /article/ │
                │    {id}       │
                └──────┬───────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
    increaseViewCount  recordView   patchDynamicFields
          │            │            │
          ▼            ▼            ▼
    ┌─────────┐  ┌──────────┐  ┌──────────┐
    │  MySQL  │  │  Redis   │  │  Redis   │
    │article_ │  │  ZSET    │  │ article: │
    │  meta   │  │leaderboard│  │detail:{id}│
    │view_cnt │  │:daily:   │  │ (缓存)    │
    │  +1     │  │  date    │  │          │
    │ (持久)  │  │ score +1 │  │          │
    └────┬────┘  └────┬─────┘  └────┬─────┘
         │            │             │
         │      ┌─────▼──────┐      │
         │      │  getTopN() │      │
         │      │ ZREVRANGE  │      │
         │      │ 取 top 10  │      │
         │      └─────┬──────┘      │
         │            │             │
         └────────────┼─────────────┘
                      │
                      ▼
              ┌───────────────┐
              │  leaderboard  │
              │  接口响应      │
              │ [{articleId,  │
              │   title,      │
              │   viewCount}] │ ← 排序用 Redis score
              └───────────────┘   展示用 DB view_count
```

---

## 五、两个计数器的关系

| | Redis ZSET score | DB `view_count` |
|---|---|---|
| 写入方式 | `ZINCRBY key 1 articleId` | `UPDATE SET view_count = view_count + 1` |
| 作用 | **排序**（谁的分高谁排前面） | **展示**（给用户看的总阅读量） |
| 时间范围 | 当日（key 按天） | 累计（从发布到现在的总和） |
| 持久化 | 无（Redis 重启丢失当日计数） | 有（MySQL） |
| 并发安全 | Redis 单线程原子操作 | `UPDATE view_count + 1` 行级锁 |

**正常情况下**，同一篇文章的当日 Redis score 和 DB view_count 增量应该一致，因为它们在同一次 `queryArticleDetail()` 调用中被同步递增。

**可能不一致的场景**：
- Redis 重启 → 当日 score 归零，DB view_count 不变 → 排行榜为空但文章详情页 view_count 正常
- 前端访问排行榜时不走 `queryArticleDetail()` → 不会触发计数

---

## 六、一致性评估

| 维度 | 状态 |
|------|:---:|
| 排名准确性（正常情况） | ✅ Redis ZSET score 和 DB 同步递增 |
| 展示值准确性 | ✅ 已改为取 DB `view_count` |
| Redis 重启恢复 | ❌ 当日排行数据丢失，需重新积累 |
| DB 故障 | ❌ `increaseViewCount` 失败直接抛异常 |
| `recordView` 失败 | ⚠️ 静默失败，不影响主流程（浏览量展示仍正确），但排行榜缺少一次计数 |

> 排行榜是系统中唯一没有 DB 持久化的社交数据。这是一项有意的设计权衡：阅读是最高频操作，每次阅读写 DB 的代价大于排行榜数据丢失的风险。排行榜丢失的核心影响只是当天热门排序暂时为空，不影响文章阅读、点赞、收藏等核心功能。
