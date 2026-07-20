package cn.sutone.ai.test.domain.content.service.article;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.service.ICommentDomainService;
import cn.sutone.ai.domain.content.service.ISocialDomainService;
import cn.sutone.ai.domain.content.service.article.ArticleDomainService;
import cn.sutone.ai.domain.content.service.cache.ArticleCacheService;
import cn.sutone.ai.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ArticleDomainService 单元测试")
@ExtendWith(MockitoExtension.class)
class ArticleDomainServiceTest {

    @Mock
    private IArticleRepository articleRepository;

    @Mock
    private ArticleCacheService articleCacheService;

    @Mock
    private ISocialDomainService socialService;

    @Mock
    private ICommentDomainService commentService;

    private ArticleDomainService articleDomainService;

    @BeforeEach
    void setUp() {
        articleDomainService = new ArticleDomainService(articleRepository, articleCacheService, socialService, commentService);
    }

    @Nested
    @DisplayName("queryArticleDetail")
    class QueryDetail {

        @Test
        @DisplayName("文章存在时返回详情并增加阅读量")
        void shouldReturnDetailAndIncreaseViewCount() {
            DraftEntity draft = DraftEntity.initNewDraft(10L, 200L, "标题", "正文", null, null);
            ArticleEntity article = ArticleEntity.publishFromDraft(500L, draft, List.of("Java"));
            when(articleCacheService.getArticleDetail(500L)).thenReturn(article);

            ArticleEntity result = articleDomainService.queryArticleDetail(500L);

            assertNotNull(result);
            assertEquals(500L, result.getArticleId());
            verify(articleRepository).increaseViewCount(500L);
            verify(socialService).recordView(500L);
        }

        @Test
        @DisplayName("文章不存在时抛异常")
        void shouldThrowWhenNotFound() {
            when(articleCacheService.getArticleDetail(999L)).thenReturn(null);
            assertThrows(AppException.class, () -> articleDomainService.queryArticleDetail(999L));
            verify(articleRepository, never()).increaseViewCount(any());
        }
    }

    @Test
    @DisplayName("queryArticlePage 正常分页")
    void shouldQueryPage() {
        DraftEntity draft = DraftEntity.initNewDraft(10L, 200L, "标题", "正文", null, null);
        List<ArticleEntity> articles = List.of(ArticleEntity.publishFromDraft(1L, draft, List.of()));
        when(articleRepository.queryArticlePage(1, 10, null, null)).thenReturn(articles);

        assertEquals(1, articleDomainService.queryArticlePage(1, 10, null, null).size());
    }

    @Test
    @DisplayName("queryArticlePage 空列表")
    void shouldReturnEmptyPage() {
        when(articleRepository.queryArticlePage(1, 10, null, null)).thenReturn(List.of());
        assertTrue(articleDomainService.queryArticlePage(1, 10, null, null).isEmpty());
    }
}
