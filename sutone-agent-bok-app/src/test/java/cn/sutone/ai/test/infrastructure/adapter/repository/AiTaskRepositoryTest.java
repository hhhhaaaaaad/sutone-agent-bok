package cn.sutone.ai.test.infrastructure.adapter.repository;

import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiTaskStatusVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingTaskTypeVO;
import cn.sutone.ai.infrastructure.adapter.repository.AiTaskRepository;
import cn.sutone.ai.infrastructure.dao.IAiTaskDao;
import cn.sutone.ai.infrastructure.dao.po.AiTaskPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AiTaskRepository 单元测试")
@ExtendWith(MockitoExtension.class)
class AiTaskRepositoryTest {

    @Mock
    private IAiTaskDao aiTaskDao;

    private AiTaskRepository aiTaskRepository;

    @BeforeEach
    void setUp() {
        aiTaskRepository = new AiTaskRepository(aiTaskDao);
    }

    @Test
    @DisplayName("nextTaskId 委托 DAO")
    void shouldDelegateNextId() {
        when(aiTaskDao.nextTaskId()).thenReturn(42L);
        assertEquals(42L, aiTaskRepository.nextTaskId());
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("Entity -> PO 转换并调用 insert")
        void shouldConvertAndInsert() {
            AiTaskEntity entity = AiTaskEntity.initPending(1L, 100L, 200L, AiWritingTaskTypeVO.GENERATE_OUTLINE, "测试 prompt", false);

            aiTaskRepository.save(entity);

            ArgumentCaptor<AiTaskPO> captor = ArgumentCaptor.forClass(AiTaskPO.class);
            verify(aiTaskDao).insert(captor.capture());
            AiTaskPO po = captor.getValue();
            assertEquals(1L, po.getId());
            assertEquals(100L, po.getUserId());
            assertEquals(200L, po.getDraftId());
            assertEquals("GENERATE_OUTLINE", po.getTaskType());
            assertEquals("测试 prompt", po.getPromptPayload());
            assertEquals(AiTaskStatusVO.PENDING.getCode(), po.getStatus());
            assertEquals(0, po.getIsDeleted());
        }
    }

    @Test
    @DisplayName("update Entity -> PO 转换并调用 update")
    void shouldConvertAndUpdate() {
        AiTaskEntity entity = AiTaskEntity.builder()
                .taskId(1L).userId(100L)
                .taskType(AiWritingTaskTypeVO.GENERATE_OUTLINE)
                .status(AiTaskStatusVO.SUCCESS)
                .responseContent("生成结果")
                .build();

        aiTaskRepository.update(entity);

        ArgumentCaptor<AiTaskPO> captor = ArgumentCaptor.forClass(AiTaskPO.class);
        verify(aiTaskDao).update(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("生成结果", captor.getValue().getResponseContent());
        assertEquals(AiTaskStatusVO.SUCCESS.getCode(), captor.getValue().getStatus());
    }

    @Nested
    @DisplayName("queryById")
    class QueryById {

        @Test
        @DisplayName("PO 存在时正确转换为 Entity")
        void shouldConvertPoToEntity() {
            AiTaskPO po = AiTaskPO.builder()
                    .id(1L).userId(100L).draftId(200L)
                    .taskType("GENERATE_OUTLINE")
                    .promptPayload("测试 prompt")
                    .responseContent("生成结果")
                    .status(AiTaskStatusVO.SUCCESS.getCode())
                    .errorMsg(null)
                    .createTime(LocalDateTime.of(2026, 7, 1, 10, 0))
                    .updateTime(LocalDateTime.of(2026, 7, 1, 10, 30))
                    .isDeleted(0)
                    .build();
            when(aiTaskDao.queryById(1L)).thenReturn(po);

            AiTaskEntity entity = aiTaskRepository.queryById(1L);
            assertNotNull(entity);
            assertEquals(1L, entity.getTaskId());
            assertEquals(100L, entity.getUserId());
            assertEquals(200L, entity.getDraftId());
            assertEquals(AiWritingTaskTypeVO.GENERATE_OUTLINE, entity.getTaskType());
            assertEquals("测试 prompt", entity.getPromptPayload());
            assertEquals("生成结果", entity.getResponseContent());
            assertEquals(AiTaskStatusVO.SUCCESS, entity.getStatus());
            assertNull(entity.getErrorMsg());
        }

        @Test
        @DisplayName("PO 为 null 时返回 null")
        void shouldReturnNullWhenPoNull() {
            when(aiTaskDao.queryById(999L)).thenReturn(null);
            assertNull(aiTaskRepository.queryById(999L));
        }

        @Test
        @DisplayName("status 为 null 时默认为 RUNNING")
        void shouldDefaultStatusWhenNull() {
            AiTaskPO po = AiTaskPO.builder().id(1L).userId(100L).taskType("GENERATE_OUTLINE").status(null).isDeleted(0).build();
            when(aiTaskDao.queryById(1L)).thenReturn(po);
            assertEquals(AiTaskStatusVO.RUNNING, aiTaskRepository.queryById(1L).getStatus());
        }
    }
}
