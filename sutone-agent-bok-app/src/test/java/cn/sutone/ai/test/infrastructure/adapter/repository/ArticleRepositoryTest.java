package cn.sutone.ai.test.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.model.valobj.ArticleStatusVO;
import cn.sutone.ai.infrastructure.adapter.repository.ArticleRepository;
import cn.sutone.ai.infrastructure.dao.IArticleDao;
import cn.sutone.ai.infrastructure.dao.IArticleMetaDao;
import cn.sutone.ai.infrastructure.dao.po.ArticleMetaPO;
import cn.sutone.ai.infrastructure.dao.po.ArticlePO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ArticleRepository 单元测试")
@ExtendWith(MockitoExtension.class)
class ArticleRepositoryTest {

    @Mock
    private IArticleDao articleDao;
    @Mock
    private IArticleMetaDao articleMetaDao;

    private ArticleRepository articleRepository;

    @BeforeEach
    void setUp() {
        articleRepository = new ArticleRepository(articleDao, articleMetaDao);
    }

    @Test
    @DisplayName("nextArticleId 委托 DAO")
    void shouldDelegateNextId() {
        when(articleDao.nextArticleId()).thenReturn(100L);
        assertEquals(100L, articleRepository.nextArticleId());
    }

    @Nested
    @DisplayName("saveArticle")
    class SaveArticle {

        @Test
        @DisplayName("文章和元数据同时落库")
        void shouldSaveArticleAndMeta() {
            DraftEntity draft = DraftEntity.initNewDraft(10L, 200L, "标题", "正文", "摘要", null);
            ArticleEntity article = ArticleEntity.publishFromDraft(500L, draft, List.of("Java", "Spring"));
            articleRepository.saveArticle(article);

            ArgumentCaptor<ArticlePO> ac = ArgumentCaptor.forClass(ArticlePO.class);
            verify(articleDao).insert(ac.capture());
            assertEquals(500L, ac.getValue().getId());
            assertEquals(ArticleStatusVO.PUBLISHED.getCode(), ac.getValue().getStatus());

            ArgumentCaptor<ArticleMetaPO> mc = ArgumentCaptor.forClass(ArticleMetaPO.class);
            verify(articleMetaDao).insert(mc.capture());
            assertEquals(500L, mc.getValue().getArticleId());
            assertTrue(mc.getValue().getTags().contains("Java"));
        }

        @Test
        @DisplayName("meta 为 null 时不插入元数据")
        void shouldSkipMetaWhenNull() {
            ArticleEntity article = ArticleEntity.builder().articleId(500L).draftId(10L).authorId(200L)
                    .title("标题").contentMd("正文").status(ArticleStatusVO.PUBLISHED)
                    .publishTime(LocalDateTime.now()).meta(null).build();
            articleRepository.saveArticle(article);

            verify(articleDao).insert(any());
            verify(articleMetaDao, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("queryArticleById")
    class QueryById {

        @Test
        @DisplayName("文章和元数据聚合返回")
        void shouldAggregateArticleAndMeta() {
            ArticlePO po = ArticlePO.builder().id(500L).draftId(10L).authorId(200L)
                    .title("标题").contentMd("正文").summary("摘要").status(1)
                    .publishTime(LocalDateTime.of(2026, 7, 1, 10, 0)).isDeleted(0).build();
            ArticleMetaPO metaPO = ArticleMetaPO.builder().articleId(500L)
                    .wordCount(5).viewCount(10).likeCount(2).favoriteCount(1)
                    .tags("A,B,C").build();
            when(articleDao.queryByArticleId(500L)).thenReturn(po);
            when(articleMetaDao.queryByArticleId(500L)).thenReturn(metaPO);

            ArticleEntity article = articleRepository.queryArticleById(500L);
            assertNotNull(article);
            assertEquals("标题", article.getTitle());
            assertEquals(10, article.getMeta().getViewCount());
            assertEquals(List.of("A", "B", "C"), article.getMeta().getTags());
        }

        @Test
        @DisplayName("文章为 null 时返回 null")
        void shouldReturnNull() {
            when(articleDao.queryByArticleId(999L)).thenReturn(null);
            assertNull(articleRepository.queryArticleById(999L));
        }
    }

    @Nested
    @DisplayName("queryArticlePage")
    class QueryPage {

        @Test
        @DisplayName("分页查询并聚合元数据")
        void shouldQueryPageWithMeta() {
            ArticlePO po = ArticlePO.builder().id(1L).title("T").contentMd("C").status(1).isDeleted(0).build();
            ArticleMetaPO metaPO = ArticleMetaPO.builder().articleId(1L).tags("X").build();
            when(articleDao.queryPage(eq(0), eq(10), isNull(), isNull())).thenReturn(List.of(po));
            when(articleMetaDao.queryByArticleId(1L)).thenReturn(metaPO);

            List<ArticleEntity> result = articleRepository.queryArticlePage(1, 10, null, null);
            assertEquals(1, result.size());
            assertEquals(List.of("X"), result.get(0).getMeta().getTags());
        }

        @Test
        @DisplayName("pageNo=2 → offset=10")
        void shouldCalculateOffset() {
            when(articleDao.queryPage(eq(10), eq(10), isNull(), isNull())).thenReturn(List.of());
            articleRepository.queryArticlePage(2, 10, null, null);
            verify(articleDao).queryPage(10, 10, null, null);
        }
    }

    @Test
    @DisplayName("countArticlePage 委托 DAO")
    void shouldDelegateCount() {
        when(articleDao.countPage(isNull(), isNull())).thenReturn(42);
        assertEquals(42, articleRepository.countArticlePage(null, null));
    }

    @Test
    @DisplayName("increaseViewCount 委托 meta DAO")
    void shouldDelegateIncreaseView() {
        articleRepository.increaseViewCount(500L);
        verify(articleMetaDao).increaseViewCount(500L);
    }
}
