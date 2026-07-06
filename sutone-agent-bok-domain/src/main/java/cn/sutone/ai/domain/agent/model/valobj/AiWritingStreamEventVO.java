package cn.sutone.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 写作流式事件值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWritingStreamEventVO {

    private String phase;
    private Chunk chunk;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chunk {
        private String type;
        private String content;
        private String raw;
    }
}
