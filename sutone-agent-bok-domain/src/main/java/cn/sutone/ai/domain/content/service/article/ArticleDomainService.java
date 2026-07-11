package cn.sutone.ai.domain.content.service.article;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.service.IArticleDomainService;
import org.springframework.stereotype.Service;
import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;

import java.util.List;

/**
 * 文章领域服务
 */
@Service
public class ArticleDomainService implements IArticleDomainService {

    private final IArticleRepository articleRepository;

    public ArticleDomainService(IArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public ArticleEntity queryArticleDetail(Long articleId) {
        ArticleEntity articleEntity = articleRepository.queryArticleById(articleId);
        if (null == articleEntity) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "文章不存在");
        }
        articleEntity.increaseViewCount();
        articleRepository.increaseViewCount(articleId);
        return articleEntity;
    }

    @Override
    public List<ArticleEntity> queryArticlePage(Integer pageNo, Integer pageSize, Long userId, String keyword) {
        return articleRepository.queryArticlePage(pageNo, pageSize, userId, keyword);
    }

    @Override
    public Integer countArticles(Long userId, String keyword) {
        return articleRepository.countArticlePage(userId, keyword);
    }
}
