package cn.sutone.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai-writing", ignoreInvalidFields = true)
public class AiWritingMqProperties {

    /** MQ Topic 配置 */
    private MqConfig mq = new MqConfig();

    /** Outbox Publisher 配置 */
    private OutboxConfig outbox = new OutboxConfig();

    /** 补偿 Recovery 配置 */
    private RecoveryConfig recovery = new RecoveryConfig();

    @Data
    public static class MqConfig {
        private String topic = "ai-writing-task";
        private String consumerGroup = "ai-writing-worker-group";
        private int consumeThreadMin = 2;
        private int consumeThreadMax = 4;
        private int maxReconsumeTimes = 3;
    }

    @Data
    public static class OutboxConfig {
        private long publishDelayMs = 2000;
        private int batchSize = 100;
        private int maxRetryCount = 5;
    }

    @Data
    public static class RecoveryConfig {
        private int runningTimeoutMinutes = 5;
        private int maxRetryCount = 3;
    }
}
