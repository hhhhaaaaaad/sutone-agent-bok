# 缓存-数据库一致性：Cache-Aside 改造方案

> 状态：待实施 | 日期：2026-07-13

---

## 一、当前问题

当前 `ArticleDomainService.queryArticleDetail()` 的缓存读写链路存在两个不一致：

### 问题 1：viewCount 不一致

```
1. 缓存命中 → 反序列化 ArticleEntity（viewCount=100）
2. articleEntity.increaseViewCount()   → 内存对象 viewCount=101
3. articleRepository.increaseViewCount(articleId) → DB viewCount=101
4. 返回给前端 viewCount=101 ✅
5. 下一次请求缓存命中 → 反序列化 ArticleEntity（viewCount=100）❌ 过期值
```

**根因**：DB 更新了，但缓存没清。下一次缓存命中返回的是旧 JSON。

### 问题 2：likeCount 不一致

```
1. 缓存命中 → 反序列化 ArticleEntity（likeCount=0，来自 DB）
2. meta.setLikeCount(socialService.getLikeCount(articleId)) → 内存对象 likeCount=5 ✅
3. 返回给前端 likeCount=5 ✅
4. 下一次请求缓存命中 → 反序列化 ArticleEntity（likeCount=0）❌ 过期值
```

**根因**：虽然当前请求内 patch 了内存对象，但缓存的 JSON 没更新。下一次命中又是旧值。

---

## 二、Cache-Aside 模式回顾

```
读:  查缓存 → hit 返回
            → miss → 查 DB → 写缓存 → 返回

写:  更新 DB → 删除缓存（不是更新缓存）
```

**为什么删缓存而不是更新缓存**：并发写场景下更新顺序无法保证，删缓存 + 懒加载最安全。

**为什么先更新 DB 再删缓存**：先删缓存再更新 DB，删后、更新前有并发读请求进来，查到旧数据回写缓存 → 永久脏数据。

---

## 三、改造方案

### 3.1 方案选择：读时实时 patch + 写后部分 evict

文章数据分为两类：

| 分类 | 字段 | 变更频率 | 处理方式 |
|------|------|---------|---------|
| 静态字段 | title, contentMd, summary, publishTime, tags | 极低（发布时） | **缓存**（TTL 30±10min） |
| 动态字段 | viewCount, likeCount, favoriteCount | 每读/每次互动 | **不缓存，实时读取** |

**核心思路**：缓存存的是快照，动态字段在返回前从实时数据源 patch。

### 3.2 具体改动

#### 改动 1：ArticleDomainService.queryArticleDetail()

```java
public ArticleEntity queryArticleDetail(Long articleId) {
    ArticleEntity articleEntity = articleCacheService.getArticleDetail(articleId);
    if (null == articleEntity) {
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "文章不存在");
    }
    // 1. 浏览量写入 DB
    articleRepository.increaseViewCount(articleId);
    // 2. 从 DB 读到最新 viewCount，patch 到缓存出来的实体上
    ArticleEntity fresh = articleRepository.queryArticleById(articleId);
    if (fresh != null && fresh.getMeta() != null && articleEntity.getMeta() != null) {
        articleEntity.getMeta().setViewCount(fresh.getMeta().getViewCount());
    }
    // 3. 实时点赞数从 Redis 读取，patch 到实体上
    if (articleEntity.getMeta() != null) {
        articleEntity.getMeta().setLikeCount(socialService.getLikeCount(articleId));
    }
    // 4. 更新热度排行榜
    socialService.recordView(articleId);
    return articleEntity;
}
```

**为什么不用 evict-on-view**：每次阅读都 evict 缓存会让缓存命中率为 0，缓存失去意义。patch 方案让 99% 的请求走缓存（命中时只多一次 `queryArticleById` 查询），只有真正的写操作（发布/重新发布）才 evict。

#### 改动 2：PublishDomainService — 已正确，无需改

`publish()` 和 `rePublishFromExisting()` 已调用 `articleCacheService.evictArticleDetail(articleId)`，符合 Cache-Aside 写后删缓存。

#### 改动 3：LikeService / FavoriteService — 无需改

点赞/收藏只操作 Redis SET，不涉及文章缓存的 DB 字段。缓存 JSON 中的 likeCount 被 patch 覆盖，不需要额外的一致性处理。

---

## 四、改造成本

| 文件 | 改动 |
|------|------|
| `ArticleDomainService.java` | `queryArticleDetail()` 增加一次 `queryArticleById` 查询 + patch viewCount |

仅改一个文件、一处方法，约 5 行代码。

---

## 五、一致性边界说明（面试讲）

### 5.1 我们能保证的

| 场景 | 一致性 | 机制 |
|------|:---:|------|
| 文章发布/重新发布 | 强一致 | 写 DB → 删缓存，下次读重建 |
| 浏览量 | 准实时 | 当前请求返回正确值；缓存命中时多查一次 DB 获取最新 viewCount |
| 点赞数 | 实时 | Redis SET 是唯一数据源，每次都实时读 |
| 收藏数 | 实时 | 同上 |

### 5.2 极端情况与兜底

| 极端情况 | 概率 | 兜底 |
|---------|------|------|
| 删缓存失败 | 低 | TTL 30min 到期自动修复 |
| `increaseViewCount` 后、`queryArticleById` 前有并发读 | 极低 | 并发读拿到 TTL 内旧值，影响一个 TTL 窗口 |
| 主从延迟（如果有读写分离） | 低 | 缓存 TTL 兜底 |

### 5.3 面试话术

> 「缓存一致性走 Cache-Aside：读时先查缓存，miss 查 DB 写缓存；写时先更新 DB 再删缓存。对于浏览量这类高频写入的字段，我没有用 evict-on-write（每次阅读都清缓存会让缓存形同虚设），而是在缓存命中后从 DB 实时 patch 浏览量，点赞数从 Redis 实时读取。这样静态字段享受缓存加速，动态字段保证准确性，99% 的请求仍然走缓存。极端情况下（删缓存失败或主从延迟）由随机 TTL 兜底，30 分钟左右自动修复。」

---

## 六、替代方案（面试对比用）

### 方案 A：evict-on-view（每次阅读删缓存）

```
viewCount++ → evict cache → 下次 miss → 查 DB → 写缓存
```

- 优点：强一致，缓存永远是 DB 的最新镜像
- 缺点：缓存命中率趋近于 0，每次阅读 = DB 查询 + 缓存重建
- 适用：极低流量系统

### 方案 B：延迟双删

```
更新 DB → 删缓存 → sleep 500ms → 再删缓存
```

- 优点：解决并发读写脏数据问题
- 缺点：增加延迟，业务侵入性强
- 适用：对一致性要求极高的场景（如电商库存）

### 方案 C：patch 实时字段（本项目采用）

```
缓存命中 → patch viewCount(DB) + likeCount(Redis) → 返回
```

- 优点：缓存命中率高，动态字段准确
- 缺点：多一次 DB 查询（仅查主键，走覆盖索引，可忽略）
- 适用：缓存数据中同时包含静态和动态字段的场景
