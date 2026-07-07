package cn.sutone.ai.test.domain.agent.service.ai_writing;

import cn.sutone.ai.domain.agent.adapter.repository.IAiTaskRepository;
import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.sutone.ai.domain.agent.model.valobj.AiTaskStatusVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingStreamEventVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingTaskTypeVO;
import cn.sutone.ai.domain.agent.service.IChatService;
import cn.sutone.ai.domain.agent.service.ai_writing.AiWritingService;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.model.valobj.DraftStatusVO;
import cn.sutone.ai.domain.content.service.draft.DraftDomainService;
import cn.sutone.ai.types.exception.AppException;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AiWritingService 单元测试")
@ExtendWith(MockitoExtension.class)
class AiWritingServiceTest {

    @Mock
    private IChatService chatService;

    @Mock
    private IAiTaskRepository aiTaskRepository;

    @Mock
    private DraftDomainService draftDomainService;

    private AiWritingService aiWritingService;

    private static final Long USER_ID = 10001L;
    private static final Long DRAFT_ID = 20001L;
    private static final Long TASK_ID = 30001L;
    private static final String AGENT_ID = "300002";

    @BeforeEach
    void setUp() {
        aiWritingService = new AiWritingService(chatService, aiTaskRepository, draftDomainService);
    }

    @Nested
    @DisplayName("submitTask")
    class SubmitTask {

        @Test
        @DisplayName("应创建 PENDING 状态的任务")
        void shouldCreatePendingTask() {
            DraftEntity draft = editingDraft();
            when(draftDomainService.queryDraftDetail(DRAFT_ID, USER_ID)).thenReturn(draft);
            when(aiTaskRepository.nextTaskId()).thenReturn(TASK_ID);

            AiTaskEntity result = aiWritingService.submitTask(USER_ID, DRAFT_ID, "GENERATE_OUTLINE", Map.of("title", "Java 入门"));

            assertNotNull(result);
            assertEquals(TASK_ID, result.getTaskId());
            assertEquals(AiTaskStatusVO.PENDING, result.getStatus());
            assertEquals(AiWritingTaskTypeVO.GENERATE_OUTLINE, result.getTaskType());
            verify(aiTaskRepository).save(any(AiTaskEntity.class));
        }

        @Test
        @DisplayName("草稿不可编辑时应抛出异常")
        void shouldThrowWhenDraftNotEditable() {
            DraftEntity draft = DraftEntity.initNewDraft(DRAFT_ID, USER_ID, "标题", "正文", null, null);
            draft.markPublished();
            when(draftDomainService.queryDraftDetail(DRAFT_ID, USER_ID)).thenReturn(draft);

            assertThrows(AppException.class, () ->
                    aiWritingService.submitTask(USER_ID, DRAFT_ID, "GENERATE_OUTLINE", null));
            verify(aiTaskRepository, never()).save(any());
        }

        @Test
        @DisplayName("无效的任务类型应抛出异常")
        void shouldThrowWhenInvalidTaskType() {
            DraftEntity draft = editingDraft();
            when(draftDomainService.queryDraftDetail(DRAFT_ID, USER_ID)).thenReturn(draft);

            assertThrows(Exception.class, () ->
                    aiWritingService.submitTask(USER_ID, DRAFT_ID, "INVALID_TYPE", null));
            verify(aiTaskRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("queryTask")
    class QueryTask {

        @Test
        @DisplayName("存在且所有权匹配时应返回任务")
        void shouldReturnTaskWhenExistsAndOwnerMatches() {
            AiTaskEntity task = pendingTask();
            when(aiTaskRepository.queryById(TASK_ID)).thenReturn(task);

            AiTaskEntity result = aiWritingService.queryTask(TASK_ID, USER_ID);

            assertNotNull(result);
            assertEquals(TASK_ID, result.getTaskId());
        }

        @Test
        @DisplayName("任务不存在时应抛出异常")
        void shouldThrowWhenTaskNotExists() {
            when(aiTaskRepository.queryById(TASK_ID)).thenReturn(null);

            assertThrows(AppException.class, () -> aiWritingService.queryTask(TASK_ID, USER_ID));
        }

        @Test
        @DisplayName("所有权不匹配时应抛出异常")
        void shouldThrowWhenOwnerMismatches() {
            AiTaskEntity task = pendingTask();
            when(aiTaskRepository.queryById(TASK_ID)).thenReturn(task);

            assertThrows(AppException.class, () -> aiWritingService.queryTask(TASK_ID, 99999L));
        }
    }

    @Nested
    @DisplayName("generateStream")
    class GenerateStream {

        @Test
        @DisplayName("成功时应完成 PENDING -> RUNNING -> SUCCESS 状态流转")
        void shouldTransitionStatesOnSuccess() {
            AiTaskEntity task = pendingTask();
            when(aiTaskRepository.queryById(TASK_ID)).thenReturn(task);
            AiAgentConfigTableVO.Agent agent = new AiAgentConfigTableVO.Agent();
            agent.setAgentId(AGENT_ID);
            when(chatService.queryAiAgentConfigList()).thenReturn(Collections.singletonList(agent));
            when(chatService.createSession(AGENT_ID, String.valueOf(USER_ID))).thenReturn("session-1");

            // 构建一个含文本的真实 Event（functionCalls/functionResponses/stringifyContent 都是 final 方法，无法 mock）
            Event realEvent = Event.builder()
                    .content(Content.fromParts(Part.fromText("生成的内容")))
                    .build();
            when(chatService.handleMessageStream(eq(AGENT_ID), eq(String.valueOf(USER_ID)), eq("session-1"), anyString()))
                    .thenReturn(Flowable.just(realEvent));

            AtomicInteger eventCount = new AtomicInteger(0);
            Consumer<AiWritingStreamEventVO> eventConsumer = event -> eventCount.incrementAndGet();

            aiWritingService.generateStream(TASK_ID, USER_ID, eventConsumer);

            // 验证 update 被调用两次（RUNNING + SUCCESS）
            verify(aiTaskRepository, times(2)).update(any(AiTaskEntity.class));
            // 最终状态应为 SUCCESS
            assertEquals(AiTaskStatusVO.SUCCESS, task.getStatus());
            assertEquals("生成的内容", task.getResponseContent());
            // 事件：2 个 status + 1 个 token + 1 个 done
            assertTrue(eventCount.get() >= 4);
        }

        @Test
        @DisplayName("流式失败时应标记为 FAILED")
        void shouldMarkFailedOnException() {
            AiTaskEntity task = pendingTask();
            when(aiTaskRepository.queryById(TASK_ID)).thenReturn(task);
            AiAgentConfigTableVO.Agent agent = new AiAgentConfigTableVO.Agent();
            agent.setAgentId(AGENT_ID);
            when(chatService.queryAiAgentConfigList()).thenReturn(Collections.singletonList(agent));
            when(chatService.createSession(AGENT_ID, String.valueOf(USER_ID))).thenReturn("session-1");
            when(chatService.handleMessageStream(eq(AGENT_ID), eq(String.valueOf(USER_ID)), eq("session-1"), anyString()))
                    .thenThrow(new RuntimeException("模型调用超时"));

            AtomicReference<String> errorContent = new AtomicReference<>();
            Consumer<AiWritingStreamEventVO> eventConsumer = event -> {
                if ("error".equals(event.getPhase()) && event.getChunk() != null) {
                    errorContent.set(event.getChunk().getContent());
                }
            };

            aiWritingService.generateStream(TASK_ID, USER_ID, eventConsumer);

            // update 至少被调用 2 次（RUNNING + FAILED）
            verify(aiTaskRepository, atLeast(2)).update(any(AiTaskEntity.class));
            // 最终状态应为 FAILED
            assertEquals(AiTaskStatusVO.FAILED, task.getStatus());
            assertTrue(task.getErrorMsg().contains("模型调用超时"));
            assertEquals("模型调用超时", errorContent.get());
        }
    }

    private static DraftEntity editingDraft() {
        return DraftEntity.initNewDraft(DRAFT_ID, USER_ID, "标题", "正文内容", "摘要", "https://cover.url");
    }

    private static AiTaskEntity pendingTask() {
        return AiTaskEntity.initPending(TASK_ID, USER_ID, DRAFT_ID, AiWritingTaskTypeVO.GENERATE_OUTLINE, "测试 prompt");
    }
}
