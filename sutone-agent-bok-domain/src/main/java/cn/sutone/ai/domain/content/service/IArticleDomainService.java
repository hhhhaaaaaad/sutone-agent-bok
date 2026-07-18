package cn.sutone.ai.domain.content.service;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;

import java.util.List;

/**
 * 文章领域服务接口
 */
public interface IArticleDomainService {

    ArticleEntity queryArticleDetail(Long articleId);

    ArticleEntity queryArticleDetailReadOnly(Long articleId);

    List<ArticleEntity> queryArticlePage(Integer pageNo, Integer pageSize, Long userId, String keyword);

    List<ArticleEntity> queryArticlePageCursor(Long cursor, Integer pageSize, Long userId, String keyword);

    Integer countArticles(Long userId, String keyword);

}
