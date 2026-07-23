package cn.sutone.ai.domain.agent.adapter.repository;

import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 任务仓储接口
 */
public interface IAiTaskRepository {

    /**
     * 保存任务并回填自增主键到 entity.taskId
     */
    Long save(AiTaskEntity aiTaskEntity);

    void update(AiTaskEntity aiTaskEntity);

    AiTaskEntity queryById(Long taskId);

    List<AiTaskEntity> queryLatestByDraftId(Long draftId, int limit);

    /**
     * 原子抢占任务 (PENDING/RETRYING -> RUNNING)，返回受影响行数
     */
    int claimTask(Long taskId, String workerId);

    void markSuccess(Long taskId, String responseContent);

    void markFailed(Long taskId, String errorMsg);

    void markRetrying(Long taskId, String errorMsg);

    void markRetryingImmediate(Long taskId, String errorMsg);

    void touchHeartbeat(Long taskId);

    /**
     * 查询心跳超时的 RUNNING 任务
     */
    List<AiTaskEntity> findStaleRunning(LocalDateTime timeout, int limit);
}
