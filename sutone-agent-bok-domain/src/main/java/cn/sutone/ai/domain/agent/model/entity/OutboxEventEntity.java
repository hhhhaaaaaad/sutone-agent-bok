package cn.sutone.ai.domain.agent.model.entity;

import cn.sutone.ai.domain.agent.model.valobj.OutboxEventVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static OutboxEventEntity newEvent(Long taskId, String eventType, String topic, String payload) {
        LocalDateTime now = LocalDateTime.now();
        return OutboxEventEntity.builder()
                .eventId(generateId())
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

    /** 毫秒时间戳左移 20 位 + 随机 20 位，单 JVM 内极低碰撞概率，多实例时间差足够区分 */
    private static long generateId() {
        long timestamp = System.currentTimeMillis();
        long random = ThreadLocalRandom.current().nextLong(0, 1L << 20);
        return (timestamp << 20) | random;
    }
}
