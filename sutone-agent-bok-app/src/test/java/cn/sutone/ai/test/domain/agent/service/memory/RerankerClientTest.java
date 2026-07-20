package cn.sutone.ai.test.domain.agent.service.memory;

import cn.sutone.ai.domain.agent.model.valobj.ScoredMemory;
import cn.sutone.ai.domain.agent.model.valobj.properties.MemoryProperties;
import cn.sutone.ai.infrastructure.adapter.repository.RerankerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RerankerClient 熔断降级测试")
class RerankerClientTest {

    private RerankerClient client;
    private MemoryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MemoryProperties();
        properties.getReranker().setEnabled(false); // 默认关闭

        client = new RerankerClient();
        try {
            var field = RerankerClient.class.getDeclaredField("memoryProperties");
            field.setAccessible(true);
            field.set(client, properties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("禁用时行为")
    class DisabledBehavior {

        @Test
        @DisplayName("rupanker 禁用时应返回粗排 top-N")
        void shouldReturnCoarseTopN() {
            List<ScoredMemory> candidates = List.of(
                    new ScoredMemory(1L, "A", 0.9, 0.5, null, null),
                    new ScoredMemory(2L, "B", 0.8, 0.5, null, null),
                    new ScoredMemory(3L, "C", 0.7, 0.5, null, null),
                    new ScoredMemory(4L, "D", 0.6, 0.5, null, null),
                    new ScoredMemory(5L, "E", 0.5, 0.5, null, null),
                    new ScoredMemory(6L, "F", 0.4, 0.5, null, null),
                    new ScoredMemory(7L, "G", 0.3, 0.5, null, null)
            );

            List<ScoredMemory> results = client.rerank("query", candidates, 3);

            assertEquals(3, results.size());
            assertEquals(1L, (long) results.get(0).id()); // original top-3 preserved
        }

        @Test
        @DisplayName("候选数 <= topN 时直接返回全部")
        void shouldReturnAllWhenFewCandidates() {
            List<ScoredMemory> candidates = List.of(
                    new ScoredMemory(1L, "A", 0.9, 0.5, null, null)
            );

            List<ScoredMemory> results = client.rerank("query", candidates, 5);

            assertEquals(1, results.size());
        }
    }

    @Nested
    @DisplayName("熔断恢复")
    class CircuitBreaker {

        @Test
        @DisplayName("初始状态 failureCount 为 0")
        void shouldStartWithZeroFailures() throws Exception {
            var field = RerankerClient.class.getDeclaredField("failureCount");
            field.setAccessible(true);
            var count = (java.util.concurrent.atomic.AtomicInteger) field.get(client);
            assertEquals(0, count.get());
        }

        @Test
        @DisplayName("熔断初始状态未开启")
        void shouldHaveCircuitOpenInitially() throws Exception {
            var field = RerankerClient.class.getDeclaredField("circuitOpenUntil");
            field.setAccessible(true);
            long openUntil = (long) field.get(client);
            assertEquals(0, openUntil);
        }
    }
}
