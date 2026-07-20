package cn.sutone.ai.domain.agent.model.valobj;

public record MemoryCandidate(
        String content,
        String type,
        String attributedTo
) {
}
