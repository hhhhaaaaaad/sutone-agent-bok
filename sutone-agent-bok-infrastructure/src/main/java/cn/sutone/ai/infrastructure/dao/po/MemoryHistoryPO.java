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
public class MemoryHistoryPO {

    private Long id;
    private Long memoryId;
    private String sessionId;
    private String oldContent;
    private String newContent;
    private String event;
    private String role;
    private LocalDateTime createTime;
}
