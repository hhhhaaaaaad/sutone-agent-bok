package cn.sutone.ai.domain.agent.model.entity;

import cn.sutone.ai.domain.agent.model.valobj.AiTaskStatusVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingTaskTypeVO;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 写作任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskEntity {

    private Long taskId;
    private Long userId;
    private Long draftId;
    private AiWritingTaskTypeVO taskType;
    private String promptPayload;
    private Boolean enableIllustration;
    private String responseContent;
    private AiTaskStatusVO status;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AiTaskEntity initPending(Long taskId, Long userId, Long draftId, AiWritingTaskTypeVO taskType, String promptPayload, Boolean enableIllustration) {
        if (null == userId) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "用户ID不能为空");
        }
        if (null == draftId) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "草稿ID不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        return AiTaskEntity.builder()
                .taskId(taskId)
                .userId(userId)
                .draftId(draftId)
                .taskType(taskType)
                .promptPayload(promptPayload)
                .enableIllustration(null != enableIllustration && enableIllustration)
                .status(AiTaskStatusVO.PENDING)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    public void startRunning() {
        if (this.status != AiTaskStatusVO.PENDING) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "只有待处理状态的任务可以开始运行");
        }
        this.status = AiTaskStatusVO.RUNNING;
        this.updateTime = LocalDateTime.now();
    }

    public void markSuccess(String responseContent) {
        this.responseContent = responseContent;
        this.status = AiTaskStatusVO.SUCCESS;
        this.errorMsg = null;
        this.updateTime = LocalDateTime.now();
    }

    public void markFailed(String errorMsg) {
        this.status = AiTaskStatusVO.FAILED;
        this.errorMsg = errorMsg;
        this.updateTime = LocalDateTime.now();
    }

    public void validateOwner(Long userId) {
        if (null == userId || !userId.equals(this.userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "无权操作该 AI 任务");
        }
    }
}
