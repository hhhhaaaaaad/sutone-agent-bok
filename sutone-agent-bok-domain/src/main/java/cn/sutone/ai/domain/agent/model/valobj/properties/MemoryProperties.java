package cn.sutone.ai.domain.agent.model.valobj.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆系统完整配置（Qdrant、Reranker、Cache、Importance、Extraction）
 */
@Data
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    /** 向量存储：memory（V1 内存） | qdrant */
    private String vectorStore = "memory";

    private Qdrant qdrant = new Qdrant();
    private Reranker reranker = new Reranker();
    private Cache cache = new Cache();
    private Importance importance = new Importance();
    private Extraction extraction = new Extraction();

    @Data
    public static class Qdrant {
        private String url = "http://localhost:6333";
        private String collection = "agent_memory";
        /** 向量维度，不配则自动探测 */
        private Integer vectorSize;
    }

    @Data
    public static class Reranker {
        private boolean enabled = false;
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String apiKey;
        private String model = "BAAI/bge-reranker-v2-m3";
        private int timeout = 3000;
    }

    @Data
    public static class Cache {
        private int profileTtlMinutes = 10;
        private int profileMaxItems = 20;
        private int searchTtlMinutes = 2;
        private double hotImportanceThreshold = 0.7;
    }

    @Data
    public static class Importance {
        private double baseWeight = 0.5;
        private double frequencyWeight = 0.3;
        private double recencyWeight = 0.2;
        private double min = 0.1;
        private double max = 1.0;
    }

    @Data
    public static class Extraction {
        private int maxContentLength = 500;
    }
}
