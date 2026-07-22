package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.agent.adapter.repository.IOutboxEventRepository;
import cn.sutone.ai.domain.agent.model.entity.OutboxEventEntity;
import cn.sutone.ai.domain.agent.model.valobj.OutboxEventVO;
import cn.sutone.ai.infrastructure.dao.IOutboxEventDao;
import cn.sutone.ai.infrastructure.dao.po.OutboxEventPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class OutboxEventRepository implements IOutboxEventRepository {

    private final IOutboxEventDao dao;

    public OutboxEventRepository(IOutboxEventDao dao) {
        this.dao = dao;
    }

    @Override
    public void save(OutboxEventEntity event) {
        dao.insert(toPO(event));
    }

    @Override
    @Transactional
    public List<OutboxEventEntity> claimPublishable(int limit) {
        return dao.claimPublishable(limit).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void markPublished(Long eventId) {
        dao.markPublished(eventId);
    }

    @Override
    public void scheduleRetry(Long eventId, String errorMsg) {
        dao.scheduleRetry(eventId, LocalDateTime.now().plusSeconds(10), errorMsg);
    }

    @Override
    public void markFailed(Long eventId, String errorMsg) {
        dao.markFailed(eventId, errorMsg);
    }

    private OutboxEventPO toPO(OutboxEventEntity entity) {
        return OutboxEventPO.builder()
                .eventId(entity.getEventId())
                .eventType(entity.getEventType())
                .aggregateId(entity.getAggregateId())
                .topic(entity.getTopic())
                .payload(entity.getPayload())
                .status(entity.getStatus().getCode())
                .retryCount(entity.getRetryCount())
                .nextRetryAt(entity.getNextRetryAt())
                .publishedAt(entity.getPublishedAt())
                .lastError(entity.getLastError())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private OutboxEventEntity toEntity(OutboxEventPO po) {
        if (null == po) return null;
        return OutboxEventEntity.builder()
                .eventId(po.getEventId())
                .eventType(po.getEventType())
                .aggregateId(po.getAggregateId())
                .topic(po.getTopic())
                .payload(po.getPayload())
                .status(po.getStatus() != null ? OutboxEventVO.valueOf(po.getStatus()) : null)
                .retryCount(po.getRetryCount())
                .nextRetryAt(po.getNextRetryAt())
                .publishedAt(po.getPublishedAt())
                .lastError(po.getLastError())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
