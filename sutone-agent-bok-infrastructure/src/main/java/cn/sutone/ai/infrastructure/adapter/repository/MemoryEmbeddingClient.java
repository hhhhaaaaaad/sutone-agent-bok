package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.agent.adapter.repository.IMemoryEmbeddingClient;
import cn.sutone.ai.domain.agent.model.valobj.properties.MemoryEmbeddingProperties;
import cn.sutone.ai.domain.agent.model.valobj.properties.MemoryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记忆系统 Embedding 客户端实现
 * 使用独立的 embedding 配置（默认硅基流动 SiliconFlow BGE 模型）
 * 与 chat 模型（DeepSeek）解耦，分别配置 base-url 和 api-key
 * <p>
 * 注意：embedding 调用使用 RestClient 直连，因为 Spring AI 的 EmbeddingRequest
 * 硬编码了 encoding_format: "float"，硅基流动 API 不支持该字段。
 */
@Slf4j
@Component
public class MemoryEmbeddingClient implements IMemoryEmbeddingClient {

    private final OpenAiApi openAiApi;
    private final RestClient restClient;
    private final String model;
    private final String embeddingsPath;
    private final int maxContentLength;

    public MemoryEmbeddingClient(MemoryEmbeddingProperties properties, MemoryProperties memoryProperties) {
        this.openAiApi = OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .embeddingsPath(properties.getEmbeddingsPath())
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = properties.getModel();
        this.embeddingsPath = properties.getEmbeddingsPath();
        this.maxContentLength = memoryProperties.getExtraction().getMaxContentLength();
        log.info("MemoryEmbeddingClient 初始化完成, baseUrl={}, embeddingsPath={}, model={}, maxContentLength={}",
                properties.getBaseUrl(), embeddingsPath, model, maxContentLength);
    }

    /** 单条文本向量化 */
    @Override
    public float[] embed(String text) {
        // 截断超长文本，避免超过模型 token 限制（BGE 最大 512 token）
        String truncated = text != null && text.length() > maxContentLength
                ? text.substring(0, maxContentLength) : text;
        try {
            var request = Map.of("input", List.of(truncated), "model", model);
            var response = restClient.post()
                    .uri(embeddingsPath)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !response.containsKey("data")) {
                log.warn("Embedding API 返回空结果, text={}", text.substring(0, Math.min(50, text.length())));
                return new float[0];
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) return new float[0];
            @SuppressWarnings("unchecked")
            List<Double> embedding = (List<Double>) data.get(0).get("embedding");
            if (embedding == null) return new float[0];
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            log.warn("Embedding API 调用失败, model={}, textLen={}",
                    model, text != null ? text.length() : 0, e);
            return new float[0];
        }
    }

    /** 批量文本向量化 */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        // 截断超长文本
        List<String> truncated = texts.stream()
                .map(t -> t != null && t.length() > maxContentLength ? t.substring(0, maxContentLength) : t)
                .toList();
        try {
            var request = Map.of("input", truncated, "model", model);
            var response = restClient.post()
                    .uri(embeddingsPath)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !response.containsKey("data")) {
                log.warn("Batch embedding API 返回空结果, size={}", texts.size());
                return fallbackEmbedOne(texts);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) return List.of();
            List<float[]> results = new ArrayList<>(data.size());
            for (Map<String, Object> item : data) {
                @SuppressWarnings("unchecked")
                List<Double> embedding = (List<Double>) item.get("embedding");
                if (embedding == null) { results.add(new float[0]); continue; }
                float[] result = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    result[i] = embedding.get(i).floatValue();
                }
                results.add(result);
            }
            return results;
        } catch (Exception e) {
            log.warn("Batch embedding 失败, 降级为逐条调用: {}", e.getMessage());
            return fallbackEmbedOne(texts);
        }
    }

    /** 获取 OpenAiApi 实例（供 MemoryExtractor 复用调用 chat completions） */
    @Override
    public OpenAiApi getOpenAiApi() {
        return openAiApi;
    }

    /** 获取模型名称 */
    @Override
    public String getModel() {
        return model;
    }

    /** 批量失败时逐条 fallback */
    private List<float[]> fallbackEmbedOne(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            try {
                results.add(embed(text));
            } catch (Exception e) {
                log.error("单条 embedding 失败, text={}: {}", text.substring(0, Math.min(30, text.length())), e.getMessage());
                results.add(new float[0]);
            }
        }
        return results;
    }
}
