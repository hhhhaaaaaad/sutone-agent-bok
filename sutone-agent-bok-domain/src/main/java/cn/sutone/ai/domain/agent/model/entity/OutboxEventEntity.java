package cn.sutone.ai.domain.agent.model.entity;

import cn.sutone.ai.domain.agent.model.valobj.OutboxEventVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox 事件实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    private Long eventId;
    private String eventType;
    private String aggregateId;
    private String topic;
    private String payload;
    private OutboxEventVO status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime publishedAt;
    private String lastError;
    private String publisherId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static OutboxEventEntity newEvent(Long taskId, String eventType, String topic, String payload) {
        LocalDateTime now = LocalDateTime.now();
        return OutboxEventEntity.builder()
                .eventType(eventType)
                .aggregateId(String.valueOf(taskId))
                .topic(topic)
                .payload(payload)
                .status(OutboxEventVO.NEW)
                .retryCount(0)
                .nextRetryAt(now)
                .createTime(now)
                .updateTime(now)
                .build();
    }
}
