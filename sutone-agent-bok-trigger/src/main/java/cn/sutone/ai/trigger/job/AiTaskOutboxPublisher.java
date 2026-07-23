package cn.sutone.ai.trigger.job;

import cn.sutone.ai.domain.agent.adapter.repository.IOutboxEventRepository;
import cn.sutone.ai.infrastructure.metrics.MqMetrics;
import cn.sutone.ai.domain.agent.model.entity.OutboxEventEntity;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox Publisher: 扫描待投递事件，可靠发送到 RocketMQ
 */
@Slf4j
@Component
public class AiTaskOutboxPublisher {

    private final IOutboxEventRepository outboxEventRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final MqMetrics mqMetrics;

    @Value("${ai-writing.outbox.batch-size:100}")
    private int batchSize;

    @Value("${ai-writing.outbox.max-retry-count:5}")
    private int maxRetry;

    @Value("${rocketmq.producer.send-message-timeout:10000}")
    private int sendTimeout;

    @Value("${ai-writing.outbox.sending-timeout-minutes:5}")
    private int sendingTimeoutMinutes;

    public AiTaskOutboxPublisher(IOutboxEventRepository outboxEventRepository,
                                  RocketMQTemplate rocketMQTemplate,
                                  MqMetrics mqMetrics) {
        this.outboxEventRepository = outboxEventRepository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.mqMetrics = mqMetrics;
    }

    @Scheduled(fixedDelayString = "${ai-writing.outbox.publish-delay-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEventEntity> events = outboxEventRepository.claimPublishable(batchSize);

        for (OutboxEventEntity event : events) {
            try {
                Object payloadObj = JSON.parse(event.getPayload());
                SendResult result = rocketMQTemplate.syncSend(event.getTopic(), payloadObj, sendTimeout);
                outboxEventRepository.markPublished(event.getEventId());
                mqMetrics.incrementPublished();
                log.info("Outbox 投递成功 eventId={} taskId={} msgId={}",
                        event.getEventId(), event.getAggregateId(), result.getMsgId());
            } catch (Exception e) {
                mqMetrics.incrementFailed();
                log.error("Outbox 投递失败 eventId={} taskId={} retry={}/{}",
                        event.getEventId(), event.getAggregateId(), event.getRetryCount(), maxRetry, e);
                int retryCount = event.getRetryCount() != null ? event.getRetryCount() : 0;
                if (retryCount >= maxRetry) {
                    outboxEventRepository.markFailed(event.getEventId(),
                            "超过最大重试次数: " + safeMsg(e));
                } else {
                    outboxEventRepository.scheduleRetry(event.getEventId(), retryCount, safeMsg(e));
                }
            }
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void recoverStaleSending() {
        int recovered = outboxEventRepository.recoverStaleSending(sendingTimeoutMinutes);
        if (recovered > 0) {
            log.warn("恢复 {} 个超时 SENDING 事件 (>{} 分钟)", recovered, sendingTimeoutMinutes);
        }
    }

    private String safeMsg(Exception e) {
        String msg = e.getMessage();
        if (null == msg || msg.isBlank()) return e.getClass().getSimpleName();
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
