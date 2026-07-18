package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.adapter.repository.ISocialRepository;
import cn.sutone.ai.infrastructure.dao.IArticleFavoriteDao;
import cn.sutone.ai.infrastructure.dao.IArticleLikeDao;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社交互动仓储实现
 */
@Repository
public class SocialRepository implements ISocialRepository {

    private final IArticleLikeDao articleLikeDao;
    private final IArticleFavoriteDao articleFavoriteDao;

    public SocialRepository(IArticleLikeDao articleLikeDao, IArticleFavoriteDao articleFavoriteDao) {
        this.articleLikeDao = articleLikeDao;
        this.articleFavoriteDao = articleFavoriteDao;
    }

    // ==================== 点赞 ====================

    @Override
    public boolean saveLike(Long articleId, Long userId) {
        return articleLikeDao.insert(articleId, userId) > 0;
    }

    @Override
    public boolean removeLike(Long articleId, Long userId) {
        return articleLikeDao.delete(articleId, userId) > 0;
    }

    @Override
    public boolean existsLike(Long articleId, Long userId) {
        return articleLikeDao.exists(articleId, userId);
    }

    @Override
    public int countLikes(Long articleId) {
        return articleLikeDao.countByArticleId(articleId);
    }

    @Override
    public Set<Long> findLikeUserIds(Long articleId) {
        return new LinkedHashSet<>(articleLikeDao.findUserIdsByArticleId(articleId));
    }

    @Override
    public Set<Long> findLikeArticleIds(Long userId) {
        return new LinkedHashSet<>(articleLikeDao.findArticleIdsByUserId(userId));
    }

    // ==================== 收藏 ====================

    @Override
    public boolean saveFavorite(Long articleId, Long userId) {
        return articleFavoriteDao.insert(articleId, userId) > 0;
    }

    @Override
    public boolean removeFavorite(Long articleId, Long userId) {
        return articleFavoriteDao.delete(articleId, userId) > 0;
    }

    @Override
    public boolean existsFavorite(Long articleId, Long userId) {
        return articleFavoriteDao.exists(articleId, userId);
    }

    @Override
    public int countFavorites(Long articleId) {
        return articleFavoriteDao.countByArticleId(articleId);
    }

    @Override
    public Set<Long> findFavoriteUserIds(Long articleId) {
        return new LinkedHashSet<>(articleFavoriteDao.findUserIdsByArticleId(articleId));
    }

    @Override
    public Set<Long> findFavoriteArticleIds(Long userId) {
        return new LinkedHashSet<>(articleFavoriteDao.findArticleIdsByUserId(userId));
    }

    @Override
    public List<Map<String, Object>> countDailyLikesByAuthor(Long userId, String since) {
        return articleLikeDao.countDailyByAuthor(userId, since);
    }

    @Override
    public List<Map<String, Object>> countDailyFavoritesByAuthor(Long userId, String since) {
        return articleFavoriteDao.countDailyByAuthor(userId, since);
    }
}
