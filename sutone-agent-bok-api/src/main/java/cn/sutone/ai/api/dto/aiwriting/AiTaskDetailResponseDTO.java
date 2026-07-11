package cn.sutone.ai.api.dto.aiwriting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 任务详情响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskDetailResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long draftId;
    private String taskType;
    private Integer status;
    private String statusDesc;
    private String responseContent;
    private String errorMsg;
    private String createTime;
    private String updateTime;
}
