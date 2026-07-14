package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.adapter.repository.ISocialRepository;
import cn.sutone.ai.infrastructure.dao.IArticleFavoriteDao;
import cn.sutone.ai.infrastructure.dao.IArticleLikeDao;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
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
    public void saveLike(Long articleId, Long userId) {
        articleLikeDao.insert(articleId, userId);
    }

    @Override
    public void removeLike(Long articleId, Long userId) {
        articleLikeDao.delete(articleId, userId);
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
    public void saveFavorite(Long articleId, Long userId) {
        articleFavoriteDao.insert(articleId, userId);
    }

    @Override
    public void removeFavorite(Long articleId, Long userId) {
        articleFavoriteDao.delete(articleId, userId);
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
}
