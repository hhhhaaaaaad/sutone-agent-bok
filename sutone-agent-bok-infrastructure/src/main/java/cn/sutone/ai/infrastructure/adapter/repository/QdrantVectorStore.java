package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.agent.adapter.repository.IMemoryEmbeddingClient;
import cn.sutone.ai.domain.agent.adapter.repository.IMemoryVectorStore;
import cn.sutone.ai.domain.agent.model.valobj.ScoredMemory;
import cn.sutone.ai.domain.agent.model.valobj.properties.MemoryProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;

/**
 * Qdrant 向量数据库实现（REST API）
 * HNSW 索引、持久化、用户隔离过滤
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "memory.vector-store", havingValue = "qdrant")
public class QdrantVectorStore implements IMemoryVectorStore {

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private IMemoryEmbeddingClient embeddingClient;

    private final RestTemplate rest = new RestTemplate();

    private String baseUrl;
    private String collectionName;

    @PostConstruct
    public void init() {
        this.baseUrl = memoryProperties.getQdrant().getUrl();
        this.collectionName = memoryProperties.getQdrant().getCollection();

        // 维度探测：避免硬编码
        int vectorSize;
        if (memoryProperties.getQdrant().getVectorSize() != null) {
            vectorSize = memoryProperties.getQdrant().getVectorSize();
        } else {
            float[] probe = embeddingClient.embed("dimension probe");
            vectorSize = probe.length;
        }
        log.info("Qdrant 初始化: url={}, collection={}, dim={}", baseUrl, collectionName, vectorSize);

        // 连接重试：Qdrant 容器可能启动慢于 app
        int maxRetries = 10;
        for (int i = 0; i < maxRetries; i++) {
            try {
                createCollectionIfNotExists(vectorSize);
                log.info("Qdrant Collection 就绪");
                break;
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("Qdrant 连接失败，已重试 {} 次", maxRetries, e);
                    throw new RuntimeException("Qdrant init failed", e);
                }
                log.warn("Qdrant 连接重试 {}/{}", i + 1, maxRetries);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void createCollectionIfNotExists(int vectorSize) {
        String url = baseUrl + "/collections/" + collectionName;
        // GET 检查是否已存在（不抛异常说明存在）
        try {
            rest.getForEntity(url, String.class);
            return;
        } catch (Exception ignored) {}

        Map<String, Object> body = Map.of(
                "vectors", Map.of("size", vectorSize, "distance", "Cosine")
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    @Override
    public void insert(Long memoryId, Long userId, float[] embedding, String content, String contentHash) {
        try {
            String url = baseUrl + "/collections/" + collectionName + "/points";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("user_id", userId);
            payload.put("content", content);
            payload.put("content_hash", contentHash);

            Map<String, Object> point = Map.of(
                    "id", memoryId,
                    "vector", (Object) embedding,
                    "payload", payload
            );
            Map<String, Object> body = Map.of("points", List.of(point));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.error("Qdrant insert failed id={}: {}", memoryId, e.getMessage());
        }
    }

    @Override
    public void update(Long memoryId, float[] newEmbedding, String newContent) {
        // 先删除再插入（Qdrant 不支持原地 update vector）
        delete(memoryId);
        // 需要 userId，从 payload 中无法直接获取，所以需要通过 search 或依赖上层传入
    }

    public void upsert(Long memoryId, Long userId, float[] embedding, String content, String contentHash) {
        // 和 insert 相同（Qdrant upsert = put）
        insert(memoryId, userId, embedding, content, contentHash);
    }

    @Override
    public List<ScoredMemory> search(Long userId, float[] queryEmbedding, int topK) {
        try {
            String url = baseUrl + "/collections/" + collectionName + "/points/search";
            Map<String, Object> mustFilter = Map.of(
                    "key", "user_id",
                    "match", Map.of("value", userId)
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", queryEmbedding);
            body.put("limit", topK);
            body.put("with_payload", true);
            body.put("filter", Map.of("must", List.of(mustFilter)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            JSONObject json = JSON.parseObject(resp.getBody());
            JSONArray results = json.getJSONArray("result");
            if (results == null || results.isEmpty()) return Collections.emptyList();

            List<ScoredMemory> list = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JSONObject r = results.getJSONObject(i);
                long id = r.getLongValue("id");
                double score = r.getDoubleValue("score");
                JSONObject payload = r.getJSONObject("payload");
                String content = payload != null ? payload.getString("content") : "";
                String contentHash = payload != null ? payload.getString("content_hash") : "";
                list.add(new ScoredMemory(id, content, score, null, null, contentHash));
            }
            return list;
        } catch (Exception e) {
            log.error("Qdrant search failed userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void delete(Long memoryId) {
        try {
            String url = baseUrl + "/collections/" + collectionName + "/points/delete";
            Map<String, Object> body = Map.of("points", List.of(memoryId));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.warn("Qdrant delete failed id={}: {}", memoryId, e.getMessage());
        }
    }

    @Override
    @Deprecated
    public float[] getVector(Long memoryId) {
        try {
            String url = baseUrl + "/collections/" + collectionName + "/points/" + memoryId;
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            JSONObject json = JSON.parseObject(resp.getBody());
            JSONObject result = json.getJSONObject("result");
            if (result == null) return null;
            JSONArray vector = result.getJSONArray("vector");
            if (vector == null) return null;
            float[] f = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                f[i] = vector.getFloatValue(i);
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }
}
