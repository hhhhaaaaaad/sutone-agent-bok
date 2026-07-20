package cn.sutone.ai.domain.agent.model.valobj.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆系统 Embedding 独立配置
 */
@Data
@ConfigurationProperties(prefix = "memory.embedding")
public class MemoryEmbeddingProperties {

    private String baseUrl = "https://api.siliconflow.cn";
    private String apiKey = "YOUR_SILICONFLOW_API_KEY";
    private String model = "BAAI/bge-large-zh-v1.5";
    private String embeddingsPath = "/v1/embeddings";
}
