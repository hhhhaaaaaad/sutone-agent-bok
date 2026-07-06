package cn.sutone.ai.test.domain.content.model.aggregate;

import cn.sutone.ai.domain.content.model.aggregate.ContentAggregate;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.model.valobj.ArticleStatusVO;
import cn.sutone.ai.domain.content.model.valobj.DraftStatusVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentAggregate 单元测试")
class ContentAggregateTest {

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("发布成功：草稿状态变为已发布，生成文章实体")
        void shouldPublishSuccessfully() {
            DraftEntity draft = DraftEntity.initNewDraft(
                    100L, 200L, "测试标题", "测试正文", "摘要", "https://cover.url");
            ContentAggregate aggregate = ContentAggregate.builder()
                    .draftEntity(draft).build();

            ArticleEntity article = aggregate.publish(500L, List.of("A", "B"));

            assertEquals(DraftStatusVO.PUBLISHED, draft.getStatus());
            assertNotNull(article);
            assertEquals(500L, article.getArticleId());
            assertEquals("测试标题", article.getTitle());
            assertEquals(ArticleStatusVO.PUBLISHED, article.getStatus());
            assertEquals(List.of("A", "B"), article.getMeta().getTags());
            assertSame(article, aggregate.getArticleEntity());
        }

        @Test
        @DisplayName("聚合的 draftEntity 和返回的 article 来自同一 draft")
        void shouldShareSameDraft() {
            DraftEntity draft = DraftEntity.initNewDraft(100L, 200L, "标题", "正文", null, null);
            ContentAggregate aggregate = ContentAggregate.builder().draftEntity(draft).build();
            ArticleEntity article = aggregate.publish(500L, List.of());

            assertEquals(draft.getDraftId(), article.getDraftId());
            assertEquals(draft.getUserId(), article.getAuthorId());
        }
    }

    @Test
    @DisplayName("Builder 可单独设置 draftEntity 和 articleEntity")
    void shouldSetBothEntities() {
        DraftEntity draft = DraftEntity.initNewDraft(100L, 200L, "标题", "正文", null, null);
        ArticleEntity article = ArticleEntity.builder().articleId(500L).build();
        ContentAggregate aggregate = ContentAggregate.builder()
                .draftEntity(draft).articleEntity(article).build();

        assertSame(draft, aggregate.getDraftEntity());
        assertSame(article, aggregate.getArticleEntity());
    }
}
