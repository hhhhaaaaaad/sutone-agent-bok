package cn.sutone.ai.domain.agent.adapter.repository;

import cn.sutone.ai.domain.agent.model.valobj.ScoredMemory;

import java.util.List;

public interface IMemoryVectorStore {

    void insert(Long memoryId, Long userId, float[] embedding, String content, String contentHash);

    void update(Long memoryId, float[] newEmbedding, String newContent);

    List<ScoredMemory> search(Long userId, float[] queryEmbedding, int topK);

    void delete(Long memoryId);

    float[] getVector(Long memoryId);
}
