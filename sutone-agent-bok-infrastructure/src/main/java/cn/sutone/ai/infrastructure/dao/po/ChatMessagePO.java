package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePO {

    private Long id;
    private Long userId;
    private String sessionId;
    private String agentId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}
