package cn.sutone.ai.api.dto.aiwriting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * SSE 流式事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWritingStreamEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String phase;
    private AiWritingChunkDTO chunk;
}
