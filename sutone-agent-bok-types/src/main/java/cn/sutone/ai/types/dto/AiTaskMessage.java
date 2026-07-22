package cn.sutone.ai.types.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RocketMQ 任务消息体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskMessage {

    private Long taskId;
    private Long eventId;
    private String createdAt;
}
