package cn.sutone.ai.api.dto.aiwriting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 提交 AI 写作任务请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAiTaskRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long draftId;
    private String taskType;
    private Map<String, Object> promptParams;
}
