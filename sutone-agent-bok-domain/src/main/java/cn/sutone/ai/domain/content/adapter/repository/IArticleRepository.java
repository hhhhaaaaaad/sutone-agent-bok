package cn.sutone.ai.domain.content.adapter.repository;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;

import java.util.List;

/**
 * 文章仓储接口
 */
public interface IArticleRepository {

    Long nextArticleId();

    Long saveArticle(ArticleEntity articleEntity);

    Long updateArticle(ArticleEntity articleEntity);

    ArticleEntity queryArticleById(Long articleId);

    ArticleEntity queryArticleByDraftId(Long draftId);

    List<ArticleEntity> queryArticlePage(Integer pageNo, Integer pageSize, Long userId, String keyword);

    List<ArticleEntity> queryArticlePageCursor(Long cursor, Integer pageSize, Long userId, String keyword);

    Integer countArticlePage(Long userId, String keyword);

    void increaseViewCount(Long articleId);

    int queryLikeCount(Long articleId);

    int queryFavoriteCount(Long articleId);

    void increaseLikeCount(Long articleId);

    void decreaseLikeCount(Long articleId);

    void increaseFavoriteCount(Long articleId);

    void decreaseFavoriteCount(Long articleId);

    List<ArticleEntity> queryByIds(List<Long> ids);

    List<Long> queryIdsByTags(List<String> tags, List<Long> excludeIds, int limit);
}
