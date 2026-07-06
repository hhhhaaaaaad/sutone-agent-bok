package cn.sutone.ai.test.domain.content.model.entity;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.model.valobj.ArticleStatusVO;
import cn.sutone.ai.types.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ArticleEntity 单元测试")
class ArticleEntityTest {

    @Nested
    @DisplayName("publishFromDraft")
    class PublishFromDraft {

        @Test
        @DisplayName("从有效草稿发布文章，字段正确映射")
        void shouldPublishSuccessfully() {
            DraftEntity draft = DraftEntity.initNewDraft(
                    100L, 200L, "测试标题", "测试正文内容", "测试摘要", "https://cover.url");
            List<String> tags = List.of("Java", "Spring");

            ArticleEntity article = ArticleEntity.publishFromDraft(500L, draft, tags);

            assertEquals(500L, article.getArticleId());
            assertEquals(100L, article.getDraftId());
            assertEquals(200L, article.getAuthorId());
            assertEquals("测试标题", article.getTitle());
            assertEquals("测试正文内容", article.getContentMd());
            assertEquals("测试摘要", article.getSummary());
            assertEquals("https://cover.url", article.getCoverUrl());
            assertEquals(ArticleStatusVO.PUBLISHED, article.getStatus());
            assertNotNull(article.getPublishTime());
            assertNotNull(article.getMeta());
            assertEquals(500L, article.getMeta().getArticleId());
            assertEquals(6, article.getMeta().getWordCount()); // "测试正文内容" length
            assertEquals(0, article.getMeta().getViewCount());
            assertEquals(tags, article.getMeta().getTags());
        }

        @Test
        @DisplayName("草稿标题为空时抛异常")
        void shouldThrowWhenDraftTitleBlank() {
            DraftEntity draft = DraftEntity.initNewDraft(100L, 200L, null, "正文", null, null);

            assertThrows(AppException.class,
                    () -> ArticleEntity.publishFromDraft(500L, draft, List.of()));
        }

        @Test
        @DisplayName("草稿正文为空时抛异常")
        void shouldThrowWhenDraftContentBlank() {
            DraftEntity draft = DraftEntity.initNewDraft(100L, 200L, "标题", "", null, null);

            assertThrows(AppException.class,
                    () -> ArticleEntity.publishFromDraft(500L, draft, List.of()));
        }

        @Test
        @DisplayName("tags 为 null 时 meta.tags 为空列表")
        void shouldHandleNullTags() {
            DraftEntity draft = DraftEntity.initNewDraft(100L, 200L, "标题", "正文", null, null);

            ArticleEntity article = ArticleEntity.publishFromDraft(500L, draft, null);

            assertNotNull(article.getMeta().getTags());
            assertTrue(article.getMeta().getTags().isEmpty());
        }

        @Test
        @DisplayName("正文为 null 时发布失败")
        void shouldThrowWhenContentNull() {
            DraftEntity draft = DraftEntity.initNewDraft(100L, 200L, "标题", null, null, null);

            assertThrows(AppException.class,
                    () -> ArticleEntity.publishFromDraft(500L, draft, List.of()));
        }
    }

    @Nested
    @DisplayName("offline")
    class Offline {

        @Test
        @DisplayName("已发布文章可下线")
        void shouldOfflinePublishedArticle() {
            ArticleEntity article = publishedArticle();

            article.offline();

            assertEquals(ArticleStatusVO.OFFLINE, article.getStatus());
        }

        @Test
        @DisplayName("已下线文章重复下线抛异常")
        void shouldThrowWhenAlreadyOffline() {
            ArticleEntity article = publishedArticle();
            article.offline();

            assertThrows(AppException.class, () -> article.offline());
        }
    }

    @Nested
    @DisplayName("increaseViewCount")
    class IncreaseViewCount {

        @Test
        @DisplayName("meta 存在时增加阅读量")
        void shouldIncreaseWhenMetaExists() {
            ArticleEntity article = publishedArticle();
            assertEquals(0, article.getMeta().getViewCount());

            article.increaseViewCount();
            assertEquals(1, article.getMeta().getViewCount());

            article.increaseViewCount();
            assertEquals(2, article.getMeta().getViewCount());
        }

        @Test
        @DisplayName("meta 为 null 时不抛异常")
        void shouldNotThrowWhenMetaNull() {
            ArticleEntity article = ArticleEntity.builder()
                    .articleId(1L)
                    .status(ArticleStatusVO.PUBLISHED)
                    .meta(null)
                    .build();

            assertDoesNotThrow(article::increaseViewCount);
        }
    }

    private static ArticleEntity publishedArticle() {
        DraftEntity draft = DraftEntity.initNewDraft(100L, 200L, "标题", "正文", null, null);
        return ArticleEntity.publishFromDraft(500L, draft, List.of("Java"));
    }
}
