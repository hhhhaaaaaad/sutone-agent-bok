package cn.sutone.ai.domain.content.service.cache;

import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.types.common.RedisKeyConstants;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class ArticleCacheService {

    private static final Logger log = LoggerFactory.getLogger(ArticleCacheService.class);
    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final Duration NULL_TTL = Duration.ofMinutes(5);
    private static final int BASE_TTL_MINUTES = 30;
    private static final int TTL_JITTER_MINUTES = 10;

    private final RedissonClient redissonClient;
    private final IArticleRepository articleRepository;

    public ArticleCacheService(RedissonClient redissonClient, IArticleRepository articleRepository) {
        this.redissonClient = redissonClient;
        this.articleRepository = articleRepository;
    }

    public ArticleEntity getArticleDetail(Long articleId) {
        String cacheKey = RedisKeyConstants.ARTICLE_DETAIL_PREFIX + articleId;
        String cached = (String) redissonClient.getBucket(cacheKey, org.redisson.client.codec.StringCodec.INSTANCE).get();
        // 1. 判断缓存是否命中
        // 1.1 缓存命中-如果缓存中存的就是"__NULL__" 则直接返回null
        if (NULL_PLACEHOLDER.equals(cached)) {
            return null;
        }
        // 1.2 缓存命中-如果缓存中存在数据，则直接返回
        if (null != cached) {
            log.debug("文章详情命中缓存 articleId={}", articleId);
            return JSON.parseObject(cached, ArticleEntity.class);
        }
        // 2. 缓存不存在（缓冲数据库压力），则加锁查询数据库
        // 2.1 获取分布式锁 key = "article:lock:" + articleId
        RLock lock = redissonClient.getLock(RedisKeyConstants.ARTICLE_LOCK_PREFIX + articleId);
        try {
            if (!lock.tryLock(2, TimeUnit.SECONDS)) {
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "系统繁忙，请稍后再试");
            }
            // DCL：获取锁后再查一次缓存
            cached = (String) redissonClient.getBucket(cacheKey, org.redisson.client.codec.StringCodec.INSTANCE).get();
            if (NULL_PLACEHOLDER.equals(cached)) {
                return null;
            }
            if (null != cached) {
                log.debug("文章详情命中缓存(锁后) articleId={}", articleId);
                return JSON.parseObject(cached, ArticleEntity.class);
            }

            ArticleEntity article = articleRepository.queryArticleById(articleId);
            if (null == article) {
                redissonClient.getBucket(cacheKey, org.redisson.client.codec.StringCodec.INSTANCE)
                        .set(NULL_PLACEHOLDER, NULL_TTL);
                return null;
            }
            Duration ttl = randomTtl();
            redissonClient.getBucket(cacheKey, org.redisson.client.codec.StringCodec.INSTANCE)
                    .set(JSON.toJSONString(article), ttl);
            log.debug("文章详情写入缓存 articleId={} ttl={}", articleId, ttl);
            return article;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void evictArticleDetail(Long articleId) {
        String cacheKey = RedisKeyConstants.ARTICLE_DETAIL_PREFIX + articleId;
        redissonClient.getBucket(cacheKey, org.redisson.client.codec.StringCodec.INSTANCE).delete();
        log.debug("文章详情缓存已清除 articleId={}", articleId);
    }

    private Duration randomTtl() {
        int minutes = BASE_TTL_MINUTES + ThreadLocalRandom.current().nextInt(TTL_JITTER_MINUTES);
        return Duration.ofMinutes(minutes);
    }
}
