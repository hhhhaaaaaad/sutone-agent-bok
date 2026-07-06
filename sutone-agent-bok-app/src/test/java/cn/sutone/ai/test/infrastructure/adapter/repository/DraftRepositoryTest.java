package cn.sutone.ai.test.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.model.valobj.DraftStatusVO;
import cn.sutone.ai.infrastructure.adapter.repository.DraftRepository;
import cn.sutone.ai.infrastructure.dao.IDraftDao;
import cn.sutone.ai.infrastructure.dao.po.DraftPO;
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
import static org.mockito.Mockito.*;

@DisplayName("DraftRepository 单元测试")
@ExtendWith(MockitoExtension.class)
class DraftRepositoryTest {

    @Mock
    private IDraftDao draftDao;

    private DraftRepository draftRepository;

    @BeforeEach
    void setUp() {
        draftRepository = new DraftRepository(draftDao);
    }

    @Test
    @DisplayName("nextDraftId 委托 DAO")
    void shouldDelegateNextId() {
        when(draftDao.nextDraftId()).thenReturn(42L);
        assertEquals(42L, draftRepository.nextDraftId());
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("Entity → PO 转换并调用 insert")
        void shouldConvertAndInsert() {
            DraftEntity entity = DraftEntity.initNewDraft(1L, 100L, "标题", "正文", "摘要", "https://cover.url");
            draftRepository.save(entity);

            ArgumentCaptor<DraftPO> captor = ArgumentCaptor.forClass(DraftPO.class);
            verify(draftDao).insert(captor.capture());
            DraftPO po = captor.getValue();
            assertEquals(1L, po.getId());
            assertEquals(100L, po.getUserId());
            assertEquals("标题", po.getTitle());
            assertEquals(DraftStatusVO.EDITING.getCode(), po.getStatus());
            assertEquals(0, po.getIsDeleted());
        }
    }

    @Test
    @DisplayName("update Entity → PO 转换并调用 update")
    void shouldConvertAndUpdate() {
        DraftEntity entity = DraftEntity.builder().draftId(1L).userId(100L)
                .title("更新").contentMd("内容").status(DraftStatusVO.EDITING).build();
        draftRepository.update(entity);

        ArgumentCaptor<DraftPO> captor = ArgumentCaptor.forClass(DraftPO.class);
        verify(draftDao).update(captor.capture());
        assertEquals("更新", captor.getValue().getTitle());
    }

    @Nested
    @DisplayName("queryById")
    class QueryById {

        @Test
        @DisplayName("PO 存在时正确转换为 Entity")
        void shouldConvertPoToEntity() {
            DraftPO po = DraftPO.builder().id(1L).userId(100L).title("标题").contentMd("正文")
                    .status(DraftStatusVO.EDITING.getCode()).isDeleted(0)
                    .createTime(LocalDateTime.of(2026, 7, 1, 10, 0))
                    .updateTime(LocalDateTime.of(2026, 7, 1, 10, 30)).build();
            when(draftDao.queryById(1L)).thenReturn(po);

            DraftEntity entity = draftRepository.queryById(1L);
            assertNotNull(entity);
            assertEquals(1L, entity.getDraftId());
            assertEquals(100L, entity.getUserId());
            assertEquals(DraftStatusVO.EDITING, entity.getStatus());
        }

        @Test
        @DisplayName("PO 为 null 时返回 null")
        void shouldReturnNullWhenPoNull() {
            when(draftDao.queryById(999L)).thenReturn(null);
            assertNull(draftRepository.queryById(999L));
        }

        @Test
        @DisplayName("status 为 null 时默认为 EDITING")
        void shouldDefaultStatusWhenNull() {
            DraftPO po = DraftPO.builder().id(1L).userId(100L).status(null).isDeleted(0).build();
            when(draftDao.queryById(1L)).thenReturn(po);
            assertEquals(DraftStatusVO.EDITING, draftRepository.queryById(1L).getStatus());
        }
    }

    @Nested
    @DisplayName("queryPage")
    class QueryPage {

        @Test
        @DisplayName("正确计算 offset 并转换列表")
        void shouldCalculateOffset() {
            DraftPO po = DraftPO.builder().id(1L).userId(100L).status(0).isDeleted(0).build();
            when(draftDao.queryPage(eq(100L), eq(0), eq(10))).thenReturn(List.of(po));

            List<DraftEntity> result = draftRepository.queryPage(100L, 1, 10);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("pageNo=3 → offset=20")
        void shouldCalculateOffsetForPage3() {
            when(draftDao.queryPage(eq(100L), eq(20), eq(10))).thenReturn(List.of());
            draftRepository.queryPage(100L, 3, 10);
            verify(draftDao).queryPage(100L, 20, 10);
        }
    }

    @Test
    @DisplayName("countByUserId 委托 DAO")
    void shouldDelegateCount() {
        when(draftDao.countByUserId(100L)).thenReturn(5);
        assertEquals(5, draftRepository.countByUserId(100L));
    }
}
