package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.agent.adapter.repository.IMemoryEmbeddingClient;
import cn.sutone.ai.domain.agent.model.valobj.properties.MemoryEmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆系统 Embedding 客户端实现
 * 使用独立的 embedding 配置（默认硅基流动 SiliconFlow BGE 模型）
 * 与 chat 模型（DeepSeek）解耦，分别配置 base-url 和 api-key
 */
@Slf4j
@Component
public class MemoryEmbeddingClient implements IMemoryEmbeddingClient {

    private final OpenAiApi openAiApi;
    private final String model;

    public MemoryEmbeddingClient(MemoryEmbeddingProperties properties) {
        this.openAiApi = OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .embeddingsPath(properties.getEmbeddingsPath())
                .build();
        this.model = properties.getModel();
        log.info("MemoryEmbeddingClient 初始化完成, baseUrl={}, embeddingsPath={}, model={}",
                properties.getBaseUrl(), properties.getEmbeddingsPath(), model);
    }

    /** 单条文本向量化 */
    @Override
    public float[] embed(String text) {
        try {
            var request = new OpenAiApi.EmbeddingRequest<>(List.of(text), model);
            var response = openAiApi.embeddings(request);
            if (response == null || response.getBody() == null
                    || response.getBody().data() == null || response.getBody().data().isEmpty()) {
                log.warn("Embedding API 返回空结果, text={}", text.substring(0, Math.min(50, text.length())));
                return new float[0];
            }
            return response.getBody().data().get(0).embedding();
        } catch (Exception e) {
            log.warn("Embedding API 调用失败: {}", e.getMessage());
            return new float[0];
        }
    }

    /** 批量文本向量化 */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            var request = new OpenAiApi.EmbeddingRequest<>(texts, model);
            var response = openAiApi.embeddings(request);
            if (response == null || response.getBody() == null || response.getBody().data() == null) {
                log.warn("Batch embedding API 返回空结果, size={}", texts.size());
                return fallbackEmbedOne(texts);
            }
            List<float[]> results = new ArrayList<>(texts.size());
            for (var item : response.getBody().data()) {
                results.add(item.embedding());
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
