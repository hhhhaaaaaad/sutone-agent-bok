package cn.sutone.ai.infrastructure.redis;

import cn.sutone.ai.domain.agent.model.valobj.AiWritingStreamEventVO;
import cn.sutone.ai.domain.agent.service.ITaskEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamReadArgs;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream 任务事件发布器
 */
@Slf4j
@Component
public class TaskEventPublisher implements ITaskEventPublisher {

    private static final String STREAM_KEY_PREFIX = "ai:task:stream:";
    private static final Duration STREAM_TTL = Duration.ofHours(24);

    private final RedissonClient redissonClient;

    public TaskEventPublisher(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void publish(Long taskId, AiWritingStreamEventVO event) {
        try {
            String key = STREAM_KEY_PREFIX + taskId;
            RStream<String, String> stream = redissonClient.getStream(key);
            Map<String, String> eventMap = Map.of(
                    "phase", nullToEmpty(event.getPhase()),
                    "type", event.getChunk() != null ? nullToEmpty(event.getChunk().getType()) : "",
                    "content", event.getChunk() != null ? nullToEmpty(event.getChunk().getContent()) : ""
            );
            // 发布事件到 Redis Stream，设置 24h TTL 防止无限增长
            stream.add(StreamAddArgs.entries(eventMap));
            stream.expire(STREAM_TTL);
        } catch (Exception e) {
            log.warn("TaskEvent 发布失败 taskId={}: {}", taskId, e.getMessage());
        }
    }

    @Override
    public void publishStatus(Long taskId, String phase, String content) {
        publish(taskId, buildEvent(phase, "status", content));
    }

    @Override
    public void publishToken(Long taskId, String phase, String content) {
        publish(taskId, buildEvent(phase, "token", content));
    }

    @Override
    public void publishDone(Long taskId) {
        publish(taskId, buildEvent("done", "done", ""));
    }

    @Override
    public void publishError(Long taskId, String errorMsg) {
        publish(taskId, buildEvent("error", "error", errorMsg));
    }

    @Override
    public List<Map.Entry<String, Map<String, String>>> readEvents(Long taskId, String lastEventId) {
        String key = STREAM_KEY_PREFIX + taskId;
        RStream<String, String> stream = redissonClient.getStream(key);
        StreamMessageId startId = parseEventId(lastEventId);
        Map<StreamMessageId, Map<String, String>> result;
        if (startId != null) {
            result = stream.read(StreamReadArgs.greaterThan(startId).count(500));
        } else {
            // 无 lastEventId：从头读取，避免漏掉已推送的事件
            result = stream.read(StreamReadArgs.greaterThan(StreamMessageId.ALL).count(500));
        }
        if (result == null || result.isEmpty()) return List.of();
        return result.entrySet().stream()
                .map(e -> Map.<String, Map<String, String>>entry(e.getKey().toString(), e.getValue()))
                .toList();
    }

    private StreamMessageId parseEventId(String id) {
        if (null == id || id.isBlank()) return null;
        try {
            String[] parts = id.split("-");
            if (parts.length == 2) return new StreamMessageId(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception ignored) {}
        return null;
    }

    private AiWritingStreamEventVO buildEvent(String phase, String type, String content) {
        return AiWritingStreamEventVO.builder()
                .phase(phase)
                .chunk(AiWritingStreamEventVO.Chunk.builder()
                        .type(type)
                        .content(content)
                        .build())
                .build();
    }

    private String nullToEmpty(String value) {
        return null == value ? "" : value;
    }
}
