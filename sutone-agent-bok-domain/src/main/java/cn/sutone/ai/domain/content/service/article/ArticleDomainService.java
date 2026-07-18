package cn.sutone.ai.domain.content.service.article;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.service.IArticleDomainService;
import cn.sutone.ai.domain.content.service.ICommentDomainService;
import cn.sutone.ai.domain.content.service.ISocialDomainService;
import cn.sutone.ai.domain.content.service.cache.ArticleCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;

import java.util.List;
@Slf4j
@Service
public class ArticleDomainService implements IArticleDomainService {

    private final IArticleRepository articleRepository;
    private final ArticleCacheService articleCacheService;
    private final ISocialDomainService socialService;
    private final ICommentDomainService commentService;

    public ArticleDomainService(IArticleRepository articleRepository, ArticleCacheService articleCacheService,
                                ISocialDomainService socialService, ICommentDomainService commentService) {
        this.articleRepository = articleRepository;
        this.articleCacheService = articleCacheService;
        this.socialService = socialService;
        this.commentService = commentService;
    }

    public ArticleEntity queryArticleDetail(Long articleId) {
        ArticleEntity articleEntity = articleCacheService.getArticleDetail(articleId);
        if (null == articleEntity) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "文章不存在");
        }
        // 增加文章的浏览量（数据库和redis缓存）
        articleRepository.increaseViewCount(articleId);
        patchDynamicFields(articleEntity, articleId);
        try {
            socialService.recordView(articleId);
        } catch (Exception e) {
            log.warn("排行榜计数失败 articleId:{}", articleId, e);
        }
        return articleEntity;
    }

    public ArticleEntity queryArticleDetailReadOnly(Long articleId) {
        ArticleEntity articleEntity = articleCacheService.getArticleDetail(articleId);
        if (null == articleEntity) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "文章不存在");
        }
        patchDynamicFields(articleEntity, articleId);
        return articleEntity;
    }

    private void patchDynamicFields(ArticleEntity articleEntity, Long articleId) {
        // 从 Redis 补计数
        if (articleEntity.getMeta() != null) {
            articleEntity.getMeta().setLikeCount(socialService.getLikeCount(articleId));
            articleEntity.getMeta().setFavoriteCount(socialService.getFavoriteCount(articleId));
            articleEntity.getMeta().setCommentCount(commentService.getCommentCount(articleId));
        }
        // 从 DB 补实时数据（浏览量、作者名、头像）
        ArticleEntity fresh = articleRepository.queryArticleById(articleId);
        if (fresh != null) {
            if (articleEntity.getMeta() != null && fresh.getMeta() != null) {
                articleEntity.getMeta().setViewCount(fresh.getMeta().getViewCount());
            }
            if (fresh.getAuthorName() != null) {
                articleEntity.setAuthorName(fresh.getAuthorName());
            }
            if (fresh.getAvatarUrl() != null) {
                articleEntity.setAvatarUrl(fresh.getAvatarUrl());
            }
        }
    }

    @Override
    public List<ArticleEntity> queryArticlePage(Integer pageNo, Integer pageSize, Long userId, String keyword) {
        return articleRepository.queryArticlePage(pageNo, pageSize, userId, keyword);
    }

    @Override
    public List<ArticleEntity> queryArticlePageCursor(Long cursor, Integer pageSize, Long userId, String keyword) {
        return articleRepository.queryArticlePageCursor(cursor, pageSize, userId, keyword);
    }

    @Override
    public Integer countArticles(Long userId, String keyword) {
        return articleRepository.countArticlePage(userId, keyword);
    }
}
