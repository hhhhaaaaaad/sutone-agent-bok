package cn.sutone.ai.domain.content.service;

import java.util.Map;
import java.util.Set;

/**
 * 文章社交互动领域服务接口
 */
public interface ISocialDomainService {

    // ==================== 点赞 ====================

    void like(Long articleId, Long userId);

    void unlike(Long articleId, Long userId);

    boolean isLiked(Long articleId, Long userId);

    int getLikeCount(Long articleId);

    Set<Long> getUserLikes(Long userId);

    // ==================== 收藏 ====================

    void favorite(Long articleId, Long userId);

    void unfavorite(Long articleId, Long userId);

    boolean isFavorited(Long articleId, Long userId);

    int getFavoriteCount(Long articleId);

    Set<Long> getUserFavorites(Long userId);

    // ==================== 排行榜 ====================

    void recordView(Long articleId);

    Map<Long, Double> getTopN(String period, int n);
}
