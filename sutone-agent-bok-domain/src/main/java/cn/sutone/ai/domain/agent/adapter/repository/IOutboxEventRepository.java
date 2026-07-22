package cn.sutone.ai.domain.agent.adapter.repository;

import cn.sutone.ai.domain.agent.model.entity.OutboxEventEntity;

import java.util.List;

/**
 * Outbox 事件仓储接口
 */
public interface IOutboxEventRepository {

    void save(OutboxEventEntity event);

    List<OutboxEventEntity> claimPublishable(int limit);

    void markPublished(Long eventId);

    void scheduleRetry(Long eventId, String errorMsg);

    void markFailed(Long eventId, String errorMsg);
}
