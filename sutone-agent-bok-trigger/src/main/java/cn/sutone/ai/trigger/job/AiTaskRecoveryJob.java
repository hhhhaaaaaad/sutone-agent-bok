package cn.sutone.ai.trigger.job;

import cn.sutone.ai.domain.agent.adapter.repository.IAiTaskRepository;
import cn.sutone.ai.domain.agent.adapter.repository.IOutboxEventRepository;
import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.entity.OutboxEventEntity;
import cn.sutone.ai.types.dto.AiTaskMessage;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 补偿 Job: 扫描心跳超时的 RUNNING 任务，回收到 RETRYING 并重新投递
 */
@Slf4j
@Component
public class AiTaskRecoveryJob {

    private final IAiTaskRepository aiTaskRepository;
    private final IOutboxEventRepository outboxEventRepository;

    @Value("${ai-writing.recovery.running-timeout-minutes:5}")
    private int timeoutMinutes;

    @Value("${ai-writing.recovery.max-retry-count:3}")
    private int maxRetry;

    @Value("${ai-writing.mq.topic:ai-writing-task}")
    private String mqTopic;

    public AiTaskRecoveryJob(IAiTaskRepository aiTaskRepository,
                              IOutboxEventRepository outboxEventRepository) {
        this.aiTaskRepository = aiTaskRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Scheduled(fixedDelay = 30_000)
    public void recoverStaleTasks() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<AiTaskEntity> staleTasks = aiTaskRepository.findStaleRunning(timeout, 50);
        log.info("补偿扫描: 发现 {} 个心跳超时 RUNNING 任务 (超时阈值 {} 分钟)", staleTasks.size(), timeoutMinutes);

        for (AiTaskEntity task : staleTasks) {
            recoverSingleTask(task);
        }
    }

    @Transactional
    public void recoverSingleTask(AiTaskEntity task) {
        int currentRetry = null == task.getRetryCount() ? 0 : task.getRetryCount();
        if (currentRetry >= maxRetry) {
            aiTaskRepository.markFailed(task.getTaskId(), "超时重试次数超限");
            log.warn("任务重试次数超限 taskId={} retry={}", task.getTaskId(), currentRetry);
            return;
        }

        aiTaskRepository.markRetrying(task.getTaskId(), "Worker 心跳超时");
        AiTaskMessage message = AiTaskMessage.builder()
                .taskId(task.getTaskId())
                .eventId(0L)
                .createdAt(LocalDateTime.now().toString())
                .build();
        OutboxEventEntity retryEvent = OutboxEventEntity.newEvent(
                task.getTaskId(), "AI_WRITING_TASK_CREATED",
                mqTopic, JSON.toJSONString(message));
        outboxEventRepository.save(retryEvent);
        log.info("补偿重试 taskId={} retry={}/{}", task.getTaskId(), currentRetry + 1, maxRetry);
    }
}
