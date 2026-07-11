package cn.sutone.ai.test.domain.content.model.entity;

import cn.sutone.ai.domain.content.model.entity.ArticleMetaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ArticleMetaEntity 单元测试")
class ArticleMetaEntityTest {

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("正常参数初始化")
        void shouldInitCorrectly() {
            ArticleMetaEntity meta = ArticleMetaEntity.init(100L, 42, List.of("Java", "Spring"));

            assertEquals(100L, meta.getArticleId());
            assertEquals(42, meta.getWordCount());
            assertEquals(0, meta.getViewCount());
            assertEquals(0, meta.getLikeCount());
            assertEquals(0, meta.getFavoriteCount());
            assertEquals(List.of("Java", "Spring"), meta.getTags());
        }

        @Test
        @DisplayName("wordCount 为 null 时默认 0")
        void shouldDefaultWordCountToZero() {
            ArticleMetaEntity meta = ArticleMetaEntity.init(100L, null, List.of());
            assertEquals(0, meta.getWordCount());
        }

        @Test
        @DisplayName("tags 为 null 时返回空列表")
        void shouldDefaultTagsToEmptyList() {
            ArticleMetaEntity meta = ArticleMetaEntity.init(100L, 10, null);
            assertNotNull(meta.getTags());
            assertTrue(meta.getTags().isEmpty());
        }

        @Test
        @DisplayName("tags 列表应为独立副本")
        void shouldCopyTagsList() {
            List<String> original = new ArrayList<>(List.of("A"));
            ArticleMetaEntity meta = ArticleMetaEntity.init(100L, 10, original);
            original.add("B");
            assertEquals(1, meta.getTags().size());
        }
    }

    @Nested
    @DisplayName("increaseViewCount")
    class IncreaseViewCount {

        @Test
        @DisplayName("正常增加阅读量")
        void shouldIncreaseViewCount() {
            ArticleMetaEntity meta = ArticleMetaEntity.init(100L, 10, List.of());
            meta.increaseViewCount();
            assertEquals(1, meta.getViewCount());
            meta.increaseViewCount();
            assertEquals(2, meta.getViewCount());
        }

        @Test
        @DisplayName("viewCount 为 null 时从 1 开始")
        void shouldStartFromOneWhenNull() {
            ArticleMetaEntity meta = ArticleMetaEntity.builder()
                    .articleId(100L).viewCount(null).build();
            meta.increaseViewCount();
            assertEquals(1, meta.getViewCount());
        }
    }
}
