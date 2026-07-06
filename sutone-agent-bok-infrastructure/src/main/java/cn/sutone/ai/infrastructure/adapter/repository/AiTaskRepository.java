package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.agent.adapter.repository.IAiTaskRepository;
import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiTaskStatusVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingTaskTypeVO;
import cn.sutone.ai.infrastructure.dao.IAiTaskDao;
import cn.sutone.ai.infrastructure.dao.po.AiTaskPO;
import org.springframework.stereotype.Repository;

/**
 * AI 任务仓储实现
 */
@Repository
public class AiTaskRepository implements IAiTaskRepository {

    private final IAiTaskDao aiTaskDao;

    public AiTaskRepository(IAiTaskDao aiTaskDao) {
        this.aiTaskDao = aiTaskDao;
    }

    @Override
    public Long nextTaskId() {
        return aiTaskDao.nextTaskId();
    }

    @Override
    public Long save(AiTaskEntity aiTaskEntity) {
        aiTaskDao.insert(toPO(aiTaskEntity));
        return aiTaskEntity.getTaskId();
    }

    @Override
    public void update(AiTaskEntity aiTaskEntity) {
        aiTaskDao.update(toPO(aiTaskEntity));
    }

    @Override
    public AiTaskEntity queryById(Long taskId) {
        return toEntity(aiTaskDao.queryById(taskId));
    }

    private AiTaskPO toPO(AiTaskEntity entity) {
        return AiTaskPO.builder()
                .id(entity.getTaskId())
                .userId(entity.getUserId())
                .draftId(entity.getDraftId())
                .taskType(entity.getTaskType().getCode())
                .promptPayload(entity.getPromptPayload())
                .responseContent(entity.getResponseContent())
                .status(entity.getStatus().getCode())
                .errorMsg(entity.getErrorMsg())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .isDeleted(0)
                .build();
    }

    private AiTaskEntity toEntity(AiTaskPO po) {
        if (null == po) {
            return null;
        }
        return AiTaskEntity.builder()
                .taskId(po.getId())
                .userId(po.getUserId())
                .draftId(po.getDraftId())
                .taskType(AiWritingTaskTypeVO.fromCode(po.getTaskType()))
                .promptPayload(po.getPromptPayload())
                .responseContent(po.getResponseContent())
                .status(AiTaskStatusVO.fromCode(po.getStatus()))
                .errorMsg(po.getErrorMsg())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
