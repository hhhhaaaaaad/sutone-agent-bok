package cn.sutone.ai.domain.agent.adapter.repository;

import cn.sutone.ai.domain.agent.model.valobj.ScoredMemory;

import java.util.List;

/**
 * Reranker 客户端接口
 */
public interface IRerankerClient {
    List<ScoredMemory> rerank(String query, List<ScoredMemory> candidates, int topN);
}
