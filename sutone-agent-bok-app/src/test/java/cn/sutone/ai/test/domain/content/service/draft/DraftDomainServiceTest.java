package cn.sutone.ai.test.domain.content.service.draft;

import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.model.valobj.DraftStatusVO;
import cn.sutone.ai.domain.content.repository.IDraftRepository;
import cn.sutone.ai.domain.content.service.command.SaveDraftCommand;
import cn.sutone.ai.domain.content.service.draft.DraftDomainService;
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

@DisplayName("DraftDomainService 单元测试")
@ExtendWith(MockitoExtension.class)
class DraftDomainServiceTest {

    @Mock
    private IDraftRepository draftRepository;

    private DraftDomainService draftDomainService;

    private static final Long USER_ID = 10001L;

    @BeforeEach
    void setUp() {
        draftDomainService = new DraftDomainService(draftRepository);
    }

    @Nested
    @DisplayName("saveDraft - 新建")
    class SaveNew {

        @Test
        @DisplayName("draftId 为 null 时应新建草稿")
        void shouldCreateNewDraft() {
            SaveDraftCommand command = SaveDraftCommand.builder()
                    .title("新标题").contentMd("新正文").summary("摘要").coverUrl("https://cover.url").build();
            when(draftRepository.nextDraftId()).thenReturn(1L);

            DraftEntity result = draftDomainService.saveDraft(USER_ID, command);

            assertNotNull(result);
            assertEquals(1L, result.getDraftId());
            assertEquals(USER_ID, result.getUserId());
            assertEquals("新标题", result.getTitle());
            assertEquals(DraftStatusVO.EDITING, result.getStatus());
            verify(draftRepository).save(any(DraftEntity.class));
        }
    }

    @Nested
    @DisplayName("saveDraft - 更新")
    class SaveUpdate {

        @Test
        @DisplayName("draftId 非空时应更新已有草稿")
        void shouldUpdateExistingDraft() {
            SaveDraftCommand command = SaveDraftCommand.builder()
                    .draftId(1L).title("更新标题").contentMd("更新正文").build();
            DraftEntity existing = DraftEntity.initNewDraft(1L, USER_ID, "旧标题", "旧正文", null, null);
            when(draftRepository.queryById(1L)).thenReturn(existing);

            DraftEntity result = draftDomainService.saveDraft(USER_ID, command);

            assertEquals("更新标题", result.getTitle());
            assertEquals("更新正文", result.getContentMd());
            verify(draftRepository).update(any(DraftEntity.class));
            verify(draftRepository, never()).save(any());
        }

        @Test
        @DisplayName("更新他人草稿应抛异常")
        void shouldThrowWhenNotOwner() {
            SaveDraftCommand command = SaveDraftCommand.builder().draftId(1L).title("x").build();
            DraftEntity existing = DraftEntity.initNewDraft(1L, 99999L, "旧标题", "旧正文", null, null);
            when(draftRepository.queryById(1L)).thenReturn(existing);

            assertThrows(AppException.class, () -> draftDomainService.saveDraft(USER_ID, command));
            verify(draftRepository, never()).update(any());
        }
    }

    @Nested
    @DisplayName("queryDraftDetail")
    class QueryDetail {

        @Test
        @DisplayName("草稿存在且是归属人，应返回详情")
        void shouldReturnDetail() {
            DraftEntity draft = DraftEntity.initNewDraft(1L, USER_ID, "标题", "正文", null, null);
            when(draftRepository.queryById(1L)).thenReturn(draft);

            DraftEntity result = draftDomainService.queryDraftDetail(1L, USER_ID);
            assertNotNull(result);
            assertEquals("标题", result.getTitle());
        }

        @Test
        @DisplayName("草稿不存在应抛异常")
        void shouldThrowWhenNotFound() {
            when(draftRepository.queryById(1L)).thenReturn(null);
            assertThrows(AppException.class, () -> draftDomainService.queryDraftDetail(1L, USER_ID));
        }
    }

    @Test
    @DisplayName("queryDraftPage 正常分页查询")
    void shouldQueryPage() {
        List<DraftEntity> drafts = List.of(
                DraftEntity.initNewDraft(1L, USER_ID, "A", "a", null, null),
                DraftEntity.initNewDraft(2L, USER_ID, "B", "b", null, null));
        when(draftRepository.queryPage(USER_ID, 1, 10)).thenReturn(drafts);

        assertEquals(2, draftDomainService.queryDraftPage(USER_ID, 1, 10).size());
    }

    @Nested
    @DisplayName("discardDraft")
    class Discard {

        @Test
        @DisplayName("归属人可废弃草稿")
        void shouldDiscardSuccessfully() {
            DraftEntity draft = DraftEntity.initNewDraft(1L, USER_ID, "标题", "正文", null, null);
            when(draftRepository.queryById(1L)).thenReturn(draft);

            DraftEntity result = draftDomainService.discardDraft(1L, USER_ID);

            assertEquals(DraftStatusVO.DISCARDED, result.getStatus());
            verify(draftRepository).update(any(DraftEntity.class));
        }

        @Test
        @DisplayName("非归属人废弃草稿应抛异常")
        void shouldThrowWhenNotOwner() {
            DraftEntity draft = DraftEntity.initNewDraft(1L, 99999L, "标题", "正文", null, null);
            when(draftRepository.queryById(1L)).thenReturn(draft);

            assertThrows(AppException.class, () -> draftDomainService.discardDraft(1L, USER_ID));
            verify(draftRepository, never()).update(any());
        }
    }
}
