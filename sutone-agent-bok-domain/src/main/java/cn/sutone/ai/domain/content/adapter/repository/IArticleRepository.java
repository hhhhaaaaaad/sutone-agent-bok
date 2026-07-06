package cn.sutone.ai.domain.content.adapter.repository;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;

import java.util.List;

/**
 * 文章仓储接口
 */
public interface IArticleRepository {

    Long nextArticleId();

    Long saveArticle(ArticleEntity articleEntity);

    ArticleEntity queryArticleById(Long articleId);

    List<ArticleEntity> queryArticlePage(Integer pageNo, Integer pageSize);

    Integer countArticlePage();

    void increaseViewCount(Long articleId);
}
