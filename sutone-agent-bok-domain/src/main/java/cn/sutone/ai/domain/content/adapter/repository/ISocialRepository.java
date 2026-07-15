package cn.sutone.ai.domain.content.adapter.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社交互动仓储接口
 */
public interface ISocialRepository {

    // ==================== 点赞 ====================

    boolean saveLike(Long articleId, Long userId);

    boolean removeLike(Long articleId, Long userId);

    boolean existsLike(Long articleId, Long userId);

    int countLikes(Long articleId);

    Set<Long> findLikeUserIds(Long articleId);

    Set<Long> findLikeArticleIds(Long userId);

    // ==================== 收藏 ====================

    boolean saveFavorite(Long articleId, Long userId);

    boolean removeFavorite(Long articleId, Long userId);

    boolean existsFavorite(Long articleId, Long userId);

    int countFavorites(Long articleId);

    Set<Long> findFavoriteUserIds(Long articleId);

    Set<Long> findFavoriteArticleIds(Long userId);

    List<Map<String, Object>> countDailyLikesByAuthor(Long userId, String since);

    List<Map<String, Object>> countDailyFavoritesByAuthor(Long userId, String since);
}
