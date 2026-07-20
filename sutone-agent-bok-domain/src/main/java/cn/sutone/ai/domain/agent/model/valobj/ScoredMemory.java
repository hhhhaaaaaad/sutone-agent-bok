package cn.sutone.ai.domain.agent.model.valobj;

import java.time.LocalDateTime;

public record ScoredMemory(
        Long id,
        String content,
        double score,
        Double importance,
        LocalDateTime lastAccessedAt,
        String contentHash
) {
    public ScoredMemory(Long id, String content, double score) {
        this(id, content, score, null, null, null);
    }
}
