package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 任务持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskPO {

    private Long id;
    private Long userId;
    private Long draftId;
    private String taskType;
    private String promptPayload;
    private String responseContent;
    private Integer status;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
