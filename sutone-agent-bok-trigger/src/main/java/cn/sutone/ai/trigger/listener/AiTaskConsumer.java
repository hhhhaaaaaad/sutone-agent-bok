package cn.sutone.ai.trigger.listener;

import cn.sutone.ai.domain.agent.adapter.repository.IAiTaskRepository;
import cn.sutone.ai.domain.agent.service.IAiWritingService;
import cn.sutone.ai.types.dto.AiTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * AI 写作任务 Consumer: 接收 RocketMQ 消息，原子抢占并执行任务
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${ai-writing.mq.topic:ai-writing-task}",
        consumerGroup = "${ai-writing.mq.consumer-group:ai-writing-worker-group}",
        maxReconsumeTimes = 3
)
public class AiTaskConsumer implements RocketMQListener<AiTaskMessage> {

    private final IAiTaskRepository aiTaskRepository;
    private final IAiWritingService aiWritingService;

    public AiTaskConsumer(IAiTaskRepository aiTaskRepository, IAiWritingService aiWritingService) {
        this.aiTaskRepository = aiTaskRepository;
        this.aiWritingService = aiWritingService;
    }

    @Override
    public void onMessage(AiTaskMessage message) {
        Long taskId = message.getTaskId();
        log.info("Consumer 收到任务 taskId={} eventId={}", taskId, message.getEventId());

        int affectedRows = aiTaskRepository.claimTask(taskId, getWorkerId());
        if (affectedRows == 0) {
            log.info("任务已被抢占或不可执行 taskId={}", taskId);
            return;
        }

        log.info("抢占成功，开始执行 taskId={}", taskId);
        aiWritingService.executeTask(taskId);
    }

    private String getWorkerId() {
        String host = System.getenv().getOrDefault("HOSTNAME", "unknown");
        return host + "-" + Thread.currentThread().getName();
    }
}
