package cn.sutone.ai.infrastructure.metrics;

import cn.sutone.ai.infrastructure.dao.IOutboxEventDao;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * MQ 可观测性指标
 */
@Component
public class MqMetrics {

    private final IOutboxEventDao outboxEventDao;

    private final AtomicLong queueSize = new AtomicLong(0);
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Timer taskExecutionTimer;

    public MqMetrics(IOutboxEventDao outboxEventDao, MeterRegistry meterRegistry) {
        this.outboxEventDao = outboxEventDao;

        this.publishedCounter = Counter.builder("outbox.published")
                .description("Successful outbox publishes")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("outbox.failed")
                .description("Failed outbox publishes")
                .register(meterRegistry);

        this.taskExecutionTimer = Timer.builder("task.execution.time")
                .description("Task execution duration")
                .register(meterRegistry);

        meterRegistry.gauge("outbox.queue.size", queueSize);
    }

    @Scheduled(fixedDelay = 30_000)
    void refreshQueueSize() {
        int count = outboxEventDao.countPublishableEvents();
        queueSize.set(count);
    }

    public void incrementPublished() {
        publishedCounter.increment();
    }

    public void incrementFailed() {
        failedCounter.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(taskExecutionTimer);
    }
}
