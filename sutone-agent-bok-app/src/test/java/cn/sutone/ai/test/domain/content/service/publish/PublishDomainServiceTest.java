package cn.sutone.ai.test.domain.content.service.publish;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.adapter.repository.IDraftRepository;
import cn.sutone.ai.domain.content.service.cache.ArticleCacheService;
import cn.sutone.ai.domain.content.service.publish.PublishDomainService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PublishDomainService 单元测试")
@ExtendWith(MockitoExtension.class)
class PublishDomainServiceTest {

    @Mock
    private IDraftRepository draftRepository;
    @Mock
    private IArticleRepository articleRepository;
    @Mock
    private ArticleCacheService articleCacheService;

    private PublishDomainService publishDomainService;
    private static final Long USER_ID = 10001L;

    @BeforeEach
    void setUp() {
        publishDomainService = new PublishDomainService(draftRepository, articleRepository, articleCacheService);
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("正常发布：草稿状态更新，文章落库")
        void shouldPublishSuccessfully() {
            DraftEntity draft = DraftEntity.initNewDraft(
                    100L, USER_ID, "标题", "正文内容", "摘要", "https://cover.url");
            when(draftRepository.queryById(100L)).thenReturn(draft);
            when(articleRepository.nextArticleId()).thenReturn(500L);

            ArticleEntity result = publishDomainService.publish(USER_ID, 100L, List.of("Java"));

            assertNotNull(result);
            assertEquals(500L, result.getArticleId());
            assertEquals("标题", result.getTitle());
            verify(draftRepository).update(any(DraftEntity.class));
            verify(articleRepository).saveArticle(any(ArticleEntity.class));
        }

        @Test
        @DisplayName("草稿不存在时抛异常")
        void shouldThrowWhenDraftNotFound() {
            when(draftRepository.queryById(999L)).thenReturn(null);
            assertThrows(AppException.class, () -> publishDomainService.publish(USER_ID, 999L, List.of()));
            verify(articleRepository, never()).saveArticle(any());
        }

        @Test
        @DisplayName("非归属人发布时抛异常")
        void shouldThrowWhenNotOwner() {
            DraftEntity draft = DraftEntity.initNewDraft(100L, 99999L, "标题", "正文", null, null);
            when(draftRepository.queryById(100L)).thenReturn(draft);
            assertThrows(AppException.class, () -> publishDomainService.publish(USER_ID, 100L, List.of()));
            verify(articleRepository, never()).saveArticle(any());
        }

        @Test
        @DisplayName("标题为空时发布失败")
        void shouldThrowWhenTitleBlank() {
            DraftEntity draft = DraftEntity.initNewDraft(100L, USER_ID, null, "正文", null, null);
            when(draftRepository.queryById(100L)).thenReturn(draft);
            assertThrows(AppException.class, () -> publishDomainService.publish(USER_ID, 100L, List.of()));
            verify(articleRepository, never()).saveArticle(any());
        }
    }
}
