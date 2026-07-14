package cn.sutone.ai.domain.content.service.social;

import cn.sutone.ai.domain.content.adapter.repository.ISocialRepository;
import cn.sutone.ai.domain.content.service.ISocialDomainService;
import cn.sutone.ai.types.common.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

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

    public SocialService(RedissonClient redissonClient, ISocialRepository socialRepository) {
        this.redissonClient = redissonClient;
        this.socialRepository = socialRepository;
    }

    // ==================== 点赞 ====================

    //非纯cache-Aside写，先更新缓存，不是直接删除缓存，如果缓存更新失败的时候再删除 redis 缓存
    @Override
    public void like(Long articleId, Long userId) {
        socialRepository.saveLike(articleId, userId);
        try {
            RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId);
            set.add(userId);
            RSet<Long> userSet = redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId);
            userSet.add(articleId);
            incrHeatScore(articleId, HEAT_LIKE_WEIGHT);
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting like keys article:{}, user:{}", articleId, userId, e);
            redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId).delete();
        }
    }

    @Override
    public void unlike(Long articleId, Long userId) {
        socialRepository.removeLike(articleId, userId);
        try {
            RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId);
            set.remove(userId);
            RSet<Long> userSet = redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId);
            userSet.remove(articleId);
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting like keys article:{}, user:{}", articleId, userId, e);
            redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_LIKE_PREFIX + userId).delete();
        }
    }

    //cache-Aside 读
    @Override
    public boolean isLiked(Long articleId, Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId);
        if (!set.isExists()) {
            Set<Long> userIds = socialRepository.findLikeUserIds(articleId);
            set.addAll(userIds);
        }
        return set.contains(userId);
    }

    @Override
    public int getLikeCount(Long articleId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_LIKE_PREFIX + articleId);
        if (!set.isExists()) {
            Set<Long> userIds = socialRepository.findLikeUserIds(articleId);
            set.addAll(userIds);
        }
        return set.size();
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

    @Override
    public void favorite(Long articleId, Long userId) {
        socialRepository.saveFavorite(articleId, userId);
        try {
            RSet<Long> articleSet = redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId);
            articleSet.add(userId);
            RSet<Long> userSet = redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId);
            userSet.add(articleId);
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting favorite keys for article:{}, user:{}", articleId, userId, e);
            redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId).delete();
        }
    }

    @Override
    public void unfavorite(Long articleId, Long userId) {
        socialRepository.removeFavorite(articleId, userId);
        try {
            RSet<Long> articleSet = redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId);
            articleSet.remove(userId);
            RSet<Long> userSet = redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId);
            userSet.remove(articleId);
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting favorite keys for article:{}, user:{}", articleId, userId, e);
            redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_FAVORITE_PREFIX + userId).delete();
        }
    }

    @Override
    public boolean isFavorited(Long articleId, Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId);
        if (!set.isExists()) {
            Set<Long> userIds = socialRepository.findFavoriteUserIds(articleId);
            if (!userIds.isEmpty()) {
                set.addAll(userIds);
            }
        }
        return set.contains(userId);
    }

    @Override
    public int getFavoriteCount(Long articleId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.ARTICLE_FAVORITE_PREFIX + articleId);
        if (!set.isExists()) {
            Set<Long> userIds = socialRepository.findFavoriteUserIds(articleId);
            if (!userIds.isEmpty()) {
                set.addAll(userIds);
            }
        }
        return set.size();
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
