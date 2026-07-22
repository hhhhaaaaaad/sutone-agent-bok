package cn.sutone.ai.trigger.job;

import cn.sutone.ai.domain.agent.adapter.repository.IOutboxEventRepository;
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

    @Value("${ai-writing.outbox.batch-size:100}")
    private int batchSize;

    @Value("${ai-writing.outbox.max-retry-count:5}")
    private int maxRetry;

    @Value("${rocketmq.producer.send-message-timeout:3000}")
    private int sendTimeout;

    public AiTaskOutboxPublisher(IOutboxEventRepository outboxEventRepository,
                                  RocketMQTemplate rocketMQTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Scheduled(fixedDelayString = "${ai-writing.outbox.publish-delay-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEventEntity> events = outboxEventRepository.claimPublishable(batchSize);

        for (OutboxEventEntity event : events) {
            try {
                Object payloadObj = JSON.parse(event.getPayload());
                SendResult result = rocketMQTemplate.syncSend(event.getTopic(), payloadObj, sendTimeout);
                outboxEventRepository.markPublished(event.getEventId());
                log.info("Outbox 投递成功 eventId={} taskId={} msgId={}",
                        event.getEventId(), event.getAggregateId(), result.getMsgId());
            } catch (Exception e) {
                log.error("Outbox 投递失败 eventId={} taskId={} retry={}/{}",
                        event.getEventId(), event.getAggregateId(), event.getRetryCount(), maxRetry, e);
                if (event.getRetryCount() != null && event.getRetryCount() >= maxRetry) {
                    outboxEventRepository.markFailed(event.getEventId(),
                            "超过最大重试次数: " + safeMsg(e));
                } else {
                    outboxEventRepository.scheduleRetry(event.getEventId(), safeMsg(e));
                }
            }
        }
    }

    private String safeMsg(Exception e) {
        String msg = e.getMessage();
        if (null == msg || msg.isBlank()) return e.getClass().getSimpleName();
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
