# 社交点赞/收藏：缓存一致性方案总结

> 面试用 | 2026-07-13

---

## 一、改造前的问题

点赞/收藏数据**只存在 Redis SET 中**，DB 没有对应记录。`article_meta` 表的 `like_count`/`favorite_count` 列只是"预留"字段，创建文章时初始化为 0，之后从未更新。

| 操作 | 数据实际在哪 | 风险 |
|------|:----------:|------|
| like / unlike | Redis SADD/SREM | Redis 重启全部丢失 |
| isLiked / getLikeCount | Redis SISMEMBER/SCARD | 无冷启动能力 |
| favorite / unfavorite | Redis SADD/SREM × 2 | 双向 SET 无 DB 备份 |
| getUserFavorites | Redis SMEMBERS | 同上 |

**本质问题**：Redis 从"缓存层"退化成了"唯一存储"，违背了「DB 是 source of truth」原则。

---

## 二、改造方案

### 2.1 整体架构

```
DB (source of truth)  ←→  Redis (cache layer)  ←→  Application
```

### 2.2 DB Schema：新建两张关系表

```sql
CREATE TABLE article_like (  -- 点赞
  article_id, user_id, create_time,
  UNIQUE KEY (article_id, user_id)  -- 防重复点赞
);

CREATE TABLE article_favorite (  -- 收藏
  article_id, user_id, create_time,
  UNIQUE KEY (article_id, user_id)
);
```

### 2.3 代码分层

```
Controller (SocialController)
    ↓
Domain Service (SocialService) —— 缓存策略在这里
    ↓                    ↓
ISocialRepository    RedissonClient
    ↓                    ↓
DAO (MyBatis)        Redis SET
    ↓
MySQL (article_like / article_favorite)
```

### 2.4 写路径：Write-Through + 故障 Evict

```
like(articleId, userId):
  1. DB INSERT IGNORE article_like(articleId, userId)  ← source of truth
  2. try {
       Redis SADD article:like:{articleId} {userId}    ← 增量更新缓存
     } catch {
       Redis DEL article:like:{articleId}              ← 故障则整 key 淘汰
     }

unlike: 同上（DB DELETE + Redis SREM + catch → delete）

favorite/unfavorite:
  DB INSERT + Redis SADD article:favorite:{articleId}
          + Redis SADD user:favorite:{userId}
  故障时两个 key 都 evict
```

### 2.5 读路径：Read-Aside（Cache Miss 自动修复）

```
isLiked(articleId, userId):
  set = article:like:{articleId}
  if set.isExists() == false:          ← cache miss
    userIds = SELECT user_id FROM article_like WHERE article_id = ?
    set.addAll(userIds)                ← 从 DB 全量回填
  return set.contains(userId)

getLikeCount / isFavorited / getFavoriteCount / getUserFavorites: 同上
```

---

## 三、为什么不是纯 Cache-Aside

| | 纯 Cache-Aside | 当前方案 |
|---|---|---|
| 写路径 | DB 写 → **删缓存** | DB 写 → **增量更新缓存**（故障时删） |
| 读路径 | miss → DB → 回填 | 同 |
| 优点 | 无部分不一致 | 缓存命中率高 |
| 缺点 | 每次写后首次读要全量加载 | 正常路径下增量更新 |

**纯 cache-aside 的问题**：热门文章每分钟几十次点赞 → 每次都删缓存 → 每次读都 `SELECT user_id FROM article_like WHERE article_id=?` 全量重建 → 缓存形同虚设。

**当前折衷**：正常路径用增量更新（SADD/SREM），只改一条数据不用重建整个 SET。仅在 Redis 故障时 evict 整 key，触发 DB 重建。用低概率的故障成本换高概率的性能收益。

---

## 四、故障场景一致性分析

| 故障 | 表现 | 恢复机制 |
|------|------|---------|
| Redis 重启 / 全量丢失 | `isExists()=false` | 首次读从 DB 重建 SET |
| DB 成功、Redis SADD 失败 | catch → evict key | 首次读从 DB 重建 SET |
| DB 成功、Redis SADD 成功但进程没收到 ACK | 可能已写也可能没写 | catch → evict key（保守策略，删了重建） |
| DB 失败 | 直接抛异常 | Redis 未更新，数据保持一致 |
| 并发 like + unlike | UNIQUE KEY 防重复 INSERT，DELETE 幂等 | DB 层面保证 |

**关键设计**：Redis 故障时不做修补（无法知道哪个条目丢没丢），而是整 key 淘汰 + DB 重建。代价是一次 DB 全量查询，但概率极低。

---

## 五、文章详情缓存的一层

点赞数/收藏数最终会出现在文章详情 API 的响应中：

```
GET /api/v1/articles/{id}  → 返回 { likeCount, favoriteCount }
```

文章详情走的是另一套缓存（`article:detail:{id}` JSON 缓存，30±10min TTL）。这里的处理是 **read-time patch**：

```java
// ArticleDomainService.queryArticleDetail()
articleEntity = articleCacheService.get(articleId);  // 缓存命中
articleEntity.getMeta().setLikeCount(socialService.getLikeCount(articleId));      // 从 Redis 实时覆盖
articleEntity.getMeta().setFavoriteCount(socialService.getFavoriteCount(articleId));
articleEntity.getMeta().setViewCount(freshFromDB.getViewCount());                 // 从 DB 实时覆盖
```

静态字段（标题/正文）享受缓存加速，动态字段（点赞数/收藏数/阅读数）每次实时 patch。这就是之前 cache-aside 文档里的「分类缓存」策略。

---

## 六、面试话术

> 「社交互动数据（点赞/收藏）我采用的是 DB 为 source of truth + Redis 为缓存层的架构。写路径走 Write-Through，先落 DB 再增量更新 Redis SET；读路径走 Read-Aside，优先读 Redis，`isExists` 检测到 miss 时从 DB 全量回填。
>
> 之所以没走纯 Cache-Aside（写后删缓存），是因为点赞/收藏存储的是 SET 数据结构而非单 key，纯 Cache-Aside 每次写后要全量重建 SET，热门文章缓存命中率会坍塌。当前方案在正常路径用 SADD/SREM 做增量更新，只在 Redis 写失败时 catch + evict 整 key，触发 DB 重建。这样缓存命中率不受影响的同时，任何故障都能自动修复。
>
> 对于文章详情页的展示，点赞数/收藏数不依赖缓存的 JSON，而是每次查询时从 Redis 实时 patch，保证用户看到的永远是最新值。」

---

## 七、改进前的状态 vs 改进后

| 维度 | 改进前 | 改进后 |
|------|--------|--------|
| 数据持久化 | 无，纯 Redis | DB 持久化，Redis 重启可恢复 |
| 一致性模型 | 无策略，Redis 裸用 | Write-Through + Read-Aside |
| 故障恢复 | 无法恢复 | `isExists()` miss + DB 重建 |
| Redis 写失败 | 数据丢失 | catch → evict → 自动重建 |
| 文章详情 likeCount | 从缓存的过期 JSON 取值 | 实时 Redis patch |
| 文章详情 favoriteCount | 永远是 0（SQL 兜底） | 实时 Redis patch |
