package cn.sutone.ai.test.domain.content.model.entity;

import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.model.valobj.DraftStatusVO;
import cn.sutone.ai.types.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DraftEntity 单元测试")
class DraftEntityTest {

    private static final Long USER_ID = 10001L;

    @Nested
    @DisplayName("initNewDraft")
    class InitNewDraft {

        @Test
        @DisplayName("应正确初始化所有字段")
        void shouldInitAllFields() {
            DraftEntity draft = DraftEntity.initNewDraft(
                    1L, USER_ID, "标题", "正文", "摘要", "https://cover.url");

            assertEquals(1L, draft.getDraftId());
            assertEquals(USER_ID, draft.getUserId());
            assertEquals("标题", draft.getTitle());
            assertEquals("正文", draft.getContentMd());
            assertEquals("摘要", draft.getSummary());
            assertEquals("https://cover.url", draft.getCoverUrl());
            assertEquals(DraftStatusVO.EDITING, draft.getStatus());
            assertNotNull(draft.getCreateTime());
            assertNotNull(draft.getUpdateTime());
            assertEquals(draft.getCreateTime(), draft.getUpdateTime());
        }

        @Test
        @DisplayName("允许标题和正文为空的新建草稿")
        void shouldAllowEmptyTitleAndContent() {
            DraftEntity draft = DraftEntity.initNewDraft(2L, USER_ID, null, null, null, null);

            assertEquals(DraftStatusVO.EDITING, draft.getStatus());
            assertNull(draft.getTitle());
            assertNull(draft.getContentMd());
        }
    }

    @Nested
    @DisplayName("updateContent")
    class UpdateContent {

        @Test
        @DisplayName("编辑中状态可正常更新")
        void shouldUpdateWhenEditing() {
            DraftEntity draft = editingDraft(1L);

            draft.updateContent("新标题", "新正文", "新摘要", "https://new.url");

            assertEquals("新标题", draft.getTitle());
            assertEquals("新正文", draft.getContentMd());
            assertEquals("新摘要", draft.getSummary());
            assertEquals("https://new.url", draft.getCoverUrl());
        }

        @Test
        @DisplayName("已发布草稿不可更新")
        void shouldThrowWhenPublished() {
            DraftEntity draft = publishedDraft(1L);

            assertThrows(AppException.class, () ->
                    draft.updateContent("新标题", "新正文", null, null));
        }

        @Test
        @DisplayName("已废弃草稿不可更新")
        void shouldThrowWhenDiscarded() {
            DraftEntity draft = discardedDraft(1L);

            assertThrows(AppException.class, () ->
                    draft.updateContent("新标题", "新正文", null, null));
        }
    }

    @Nested
    @DisplayName("discard")
    class Discard {

        @Test
        @DisplayName("编辑中草稿可废弃")
        void shouldDiscardWhenEditing() {
            DraftEntity draft = editingDraft(1L);

            draft.discard();

            assertEquals(DraftStatusVO.DISCARDED, draft.getStatus());
        }

        @Test
        @DisplayName("已发布草稿不可废弃")
        void shouldThrowWhenPublished() {
            DraftEntity draft = publishedDraft(1L);

            assertThrows(AppException.class, () -> draft.discard());
        }

        @Test
        @DisplayName("已废弃草稿不可重复废弃")
        void shouldThrowWhenAlreadyDiscarded() {
            DraftEntity draft = discardedDraft(1L);

            assertThrows(AppException.class, () -> draft.discard());
        }
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("编辑中且标题正文非空，可标记发布")
        void shouldMarkPublishedWhenValid() {
            DraftEntity draft = editingDraft(1L);

            draft.markPublished();

            assertEquals(DraftStatusVO.PUBLISHED, draft.getStatus());
        }

        @Test
        @DisplayName("标题为空时不可发布")
        void shouldThrowWhenTitleBlank() {
            DraftEntity draft = DraftEntity.initNewDraft(1L, USER_ID, null, "正文", null, null);

            assertThrows(AppException.class, () -> draft.markPublished());
        }

        @Test
        @DisplayName("正文为空时不可发布")
        void shouldThrowWhenContentBlank() {
            DraftEntity draft = DraftEntity.initNewDraft(1L, USER_ID, "标题", null, null, null);

            assertThrows(AppException.class, () -> draft.markPublished());
        }

        @Test
        @DisplayName("标题和正文均为空字符串时不可发布")
        void shouldThrowWhenBothBlank() {
            DraftEntity draft = DraftEntity.initNewDraft(1L, USER_ID, "", "   ", null, null);

            assertThrows(AppException.class, () -> draft.markPublished());
        }

        @Test
        @DisplayName("已发布草稿不可重复标记")
        void shouldThrowWhenAlreadyPublished() {
            DraftEntity draft = publishedDraft(1L);

            assertThrows(AppException.class, () -> draft.markPublished());
        }
    }

    @Nested
    @DisplayName("checkEditable")
    class CheckEditable {

        @Test
        @DisplayName("编辑中状态不抛异常")
        void shouldNotThrowWhenEditing() {
            DraftEntity draft = editingDraft(1L);
            assertDoesNotThrow(draft::checkEditable);
        }

        @Test
        @DisplayName("已发布状态抛异常")
        void shouldThrowWhenPublished() {
            DraftEntity draft = publishedDraft(1L);
            assertThrows(AppException.class, draft::checkEditable);
        }

        @Test
        @DisplayName("已废弃状态抛异常")
        void shouldThrowWhenDiscarded() {
            DraftEntity draft = discardedDraft(1L);
            assertThrows(AppException.class, draft::checkEditable);
        }
    }

    @Nested
    @DisplayName("validateOwner")
    class ValidateOwner {

        @Test
        @DisplayName("草稿归属人校验通过")
        void shouldPassWhenOwnerMatches() {
            DraftEntity draft = editingDraft(1L);

            assertDoesNotThrow(() -> draft.validateOwner(USER_ID));
        }

        @Test
        @DisplayName("非归属人校验失败")
        void shouldThrowWhenOwnerMismatches() {
            DraftEntity draft = editingDraft(1L);

            assertThrows(AppException.class, () -> draft.validateOwner(99999L));
        }

        @Test
        @DisplayName("userId 为 null 时校验失败")
        void shouldThrowWhenUserIdNull() {
            DraftEntity draft = editingDraft(1L);

            assertThrows(AppException.class, () -> draft.validateOwner(null));
        }
    }

    private static DraftEntity editingDraft(Long draftId) {
        return DraftEntity.initNewDraft(draftId, USER_ID, "标题", "正文内容", "摘要", "https://cover.url");
    }

    private static DraftEntity publishedDraft(Long draftId) {
        DraftEntity draft = editingDraft(draftId);
        draft.markPublished();
        return draft;
    }

    private static DraftEntity discardedDraft(Long draftId) {
        DraftEntity draft = editingDraft(draftId);
        draft.discard();
        return draft;
    }
}
