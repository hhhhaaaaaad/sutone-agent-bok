package cn.sutone.ai.domain.agent.adapter.repository;

import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

/**
 * Embedding 客户端接口，Domain 层通过此接口使用 embedding 能力
 */
public interface IMemoryEmbeddingClient {

    /** 单条文本向量化 */
    float[] embed(String text);

    /** 批量文本向量化 */
    List<float[]> embedBatch(List<String> texts);

    /** 获取 OpenAiApi 实例（供 LLM 抽取时复用） */
    OpenAiApi getOpenAiApi();

    /** 获取模型名称 */
    String getModel();
}
