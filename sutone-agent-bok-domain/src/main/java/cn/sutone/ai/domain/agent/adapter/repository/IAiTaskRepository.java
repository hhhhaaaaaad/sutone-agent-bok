package cn.sutone.ai.domain.agent.adapter.repository;

import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;

/**
 * AI 任务仓储接口
 */
public interface IAiTaskRepository {

    Long nextTaskId();

    Long save(AiTaskEntity aiTaskEntity);

    void update(AiTaskEntity aiTaskEntity);

    AiTaskEntity queryById(Long taskId);
}
