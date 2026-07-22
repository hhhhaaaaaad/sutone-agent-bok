package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.agent.adapter.repository.IAiTaskRepository;
import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiTaskStatusVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingTaskTypeVO;
import cn.sutone.ai.infrastructure.dao.IAiTaskDao;
import cn.sutone.ai.infrastructure.dao.po.AiTaskPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public List<AiTaskEntity> queryLatestByDraftId(Long draftId, int limit) {
        return aiTaskDao.queryByDraftId(draftId, limit).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public int claimTask(Long taskId, String workerId) {
        return aiTaskDao.claimTask(taskId, AiTaskStatusVO.RUNNING.getCode(), workerId);
    }

    @Override
    public void markSuccess(Long taskId, String responseContent) {
        aiTaskDao.markSuccess(taskId, responseContent);
    }

    @Override
    public void markFailed(Long taskId, String errorMsg) {
        aiTaskDao.markFailed(taskId, errorMsg);
    }

    @Override
    public void markRetrying(Long taskId, String errorMsg) {
        aiTaskDao.markRetrying(taskId, errorMsg);
    }

    @Override
    public void touchHeartbeat(Long taskId) {
        aiTaskDao.touchHeartbeat(taskId);
    }

    @Override
    public List<AiTaskEntity> findStaleRunning(LocalDateTime timeout, int limit) {
        return aiTaskDao.findStaleRunning(timeout, limit).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private AiTaskPO toPO(AiTaskEntity entity) {
        return AiTaskPO.builder()
                .id(entity.getTaskId())
                .userId(entity.getUserId())
                .draftId(entity.getDraftId())
                .taskType(entity.getTaskType().getCode())
                .promptPayload(entity.getPromptPayload())
                .enableIllustration(Boolean.TRUE.equals(entity.getEnableIllustration()) ? 1 : 0)
                .responseContent(entity.getResponseContent())
                .status(entity.getStatus().getCode())
                .errorMsg(entity.getErrorMsg())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .startedAt(entity.getStartedAt())
                .heartbeatAt(entity.getHeartbeatAt())
                .retryCount(entity.getRetryCount())
                .nextRetryAt(entity.getNextRetryAt())
                .workerId(entity.getWorkerId())
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
                .enableIllustration(null != po.getEnableIllustration() && po.getEnableIllustration() == 1)
                .responseContent(po.getResponseContent())
                .status(AiTaskStatusVO.fromCode(po.getStatus()))
                .errorMsg(po.getErrorMsg())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .startedAt(po.getStartedAt())
                .heartbeatAt(po.getHeartbeatAt())
                .retryCount(po.getRetryCount())
                .nextRetryAt(po.getNextRetryAt())
                .workerId(po.getWorkerId())
                .build();
    }
}
