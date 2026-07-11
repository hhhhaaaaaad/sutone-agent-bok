package cn.sutone.ai.api.dto.aiwriting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * SSE 流式事件体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWritingChunkDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private String content;
    private String raw;
}
