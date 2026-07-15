package cn.sutone.ai.domain.content.service.social;

import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.adapter.repository.ISocialRepository;
import cn.sutone.ai.domain.content.service.ISocialDomainService;
import cn.sutone.ai.types.common.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SocialService implements ISocialDomainService {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RedissonClient redissonClient;
    private final ISocialRepository socialRepository;
    private final IArticleRepository articleRepository;

    public SocialService(RedissonClient redissonClient, ISocialRepository socialRepository,
                         IArticleRepository articleRepository) {
        this.redissonClient = redissonClient;
        this.socialRepository = socialRepository;
        this.articleRepository = articleRepository;
    }

    // ==================== 点赞 ====================

    @Transactional
    @Override
    public void like(Long articleId, Long userId) {
        if (!socialRepository.saveLike(articleId, userId)) {
            return; // 已点过赞，跳过计数
        }
        articleRepository.increaseLikeCount(articleId);
        try {
            RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId);
            set.add(userId);
            RAtomicLong counter = redissonClient.getAtomicLong(
                    RedisKeyConstants.ARTICLE_LIKE_COUNT_PREFIX + articleId);
            counter.incrementAndGet();
            RSet<Long> userSet = redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId);
            userSet.add(articleId);
            incrHeatScore(articleId, HEAT_LIKE_WEIGHT);
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting like keys article:{}, user:{}", articleId, userId, e);
            redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.ARTICLE_LIKE_COUNT_PREFIX + articleId).delete();
        }
    }

    @Transactional
    @Override
    public void unlike(Long articleId, Long userId) {
        if (!socialRepository.removeLike(articleId, userId)) {
            return;
        }
        articleRepository.decreaseLikeCount(articleId);
        try {
            RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId);
            set.remove(userId);
            RAtomicLong counter = redissonClient.getAtomicLong(
                    RedisKeyConstants.ARTICLE_LIKE_COUNT_PREFIX + articleId);
            counter.decrementAndGet();
            RSet<Long> userSet = redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId);
            userSet.remove(articleId);
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting like keys article:{}, user:{}", articleId, userId, e);
            redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.ARTICLE_LIKE_COUNT_PREFIX + articleId).delete();
        }
    }

    @Override
    public boolean isLiked(Long articleId, Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId);
        if (set.isExists()) {
            return set.contains(userId);
        }
        boolean exists = socialRepository.existsLike(articleId, userId);
        if (exists) {
            set.add(userId);
        }
        return exists;
    }

    @Override
    public int getLikeCount(Long articleId) {
        RAtomicLong counter = redissonClient.getAtomicLong(
                RedisKeyConstants.ARTICLE_LIKE_COUNT_PREFIX + articleId);
        if (counter.isExists()) {
            return (int) counter.get();
        }
        int dbCount = articleRepository.queryLikeCount(articleId);
        counter.set(dbCount);
        return dbCount;
    }

    @Override
    public Set<Long> getUserLikes(Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId);
        if (!set.isExists()) {
            Set<Long> articleIds = socialRepository.findLikeArticleIds(userId);
            set.addAll(articleIds);
        }
        return set.readAll();
    }

    // ==================== 收藏 ====================

    @Transactional
    @Override
    public void favorite(Long articleId, Long userId) {
        if (!socialRepository.saveFavorite(articleId, userId)) {
            return;
        }
        articleRepository.increaseFavoriteCount(articleId);
        try {
            RSet<Long> articleSet = redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId);
            articleSet.add(userId);
            RAtomicLong counter = redissonClient.getAtomicLong(
                    RedisKeyConstants.ARTICLE_FAVORITE_COUNT_PREFIX + articleId);
            counter.incrementAndGet();
            RSet<Long> userSet = redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId);
            userSet.add(articleId);
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting favorite keys for article:{}, user:{}", articleId, userId, e);
            redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.ARTICLE_FAVORITE_COUNT_PREFIX + articleId).delete();
        }
    }

    @Transactional
    @Override
    public void unfavorite(Long articleId, Long userId) {
        if (!socialRepository.removeFavorite(articleId, userId)) {
            return;
        }
        articleRepository.decreaseFavoriteCount(articleId);
        try {
            RSet<Long> articleSet = redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId);
            articleSet.remove(userId);
            RAtomicLong counter = redissonClient.getAtomicLong(
                    RedisKeyConstants.ARTICLE_FAVORITE_COUNT_PREFIX + articleId);
            counter.decrementAndGet();
            RSet<Long> userSet = redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId);
            userSet.remove(articleId);
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting favorite keys for article:{}, user:{}", articleId, userId, e);
            redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.ARTICLE_FAVORITE_COUNT_PREFIX + articleId).delete();
        }
    }

    @Override
    public boolean isFavorited(Long articleId, Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId);
        if (set.isExists()) {
            return set.contains(userId);
        }
        boolean exists = socialRepository.existsFavorite(articleId, userId);
        if (exists) {
            set.add(userId);
        }
        return exists;
    }

    @Override
    public int getFavoriteCount(Long articleId) {
        RAtomicLong counter = redissonClient.getAtomicLong(
                RedisKeyConstants.ARTICLE_FAVORITE_COUNT_PREFIX + articleId);
        if (counter.isExists()) {
            return (int) counter.get();
        }
        int dbCount = articleRepository.queryFavoriteCount(articleId);
        counter.set(dbCount);
        return dbCount;
    }

    @Override
    public Set<Long> getUserFavorites(Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId);
        if (!set.isExists()) {
            Set<Long> articleIds = socialRepository.findFavoriteArticleIds(userId);
            set.addAll(articleIds);
        }
        return set.readAll();
    }

    // ==================== 排行榜 ====================

    private static final Duration LEADERBOARD_TTL = Duration.ofDays(2);
    /** 排行榜热度权重：每次浏览 +1 */
    private static final int HEAT_VIEW_WEIGHT = 1;
    /** 排行榜热度权重：每次点赞 +3 */
    private static final int HEAT_LIKE_WEIGHT = 3;

    @Override
    public void recordView(Long articleId) {
        incrHeatScore(articleId, HEAT_VIEW_WEIGHT);
    }

    private void incrHeatScore(Long articleId, int weight) {
        String todayKey = buildKey("daily", LocalDate.now());
        RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(todayKey);
        set.addScore(articleId, weight);
        set.expire(LEADERBOARD_TTL);
    }

    @Override
    public Map<Long, Double> getTopN(String period, int n) {
        String key = buildKey(period, LocalDate.now());
        RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(key);
        Collection<Long> topIds = set.valueRangeReversed(0, n - 1);
        Map<Long, Double> result = new LinkedHashMap<>();
        for (Long articleId : topIds) {
            Double score = set.getScore(articleId);
            result.put(articleId, score != null ? score : 0.0);
        }
        return result;
    }

    private String buildKey(String period, LocalDate date) {
        return RedisKeyConstants.LEADERBOARD_VIEW_PREFIX + period + ":" + date.format(DF);
    }
}
