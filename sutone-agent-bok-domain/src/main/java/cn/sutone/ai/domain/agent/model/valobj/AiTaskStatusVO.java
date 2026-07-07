package cn.sutone.ai.domain.agent.model.valobj;

import lombok.Getter;

/**
 * AI 任务状态
 */
@Getter
public enum AiTaskStatusVO {

    PENDING(3, "待处理"),
    RUNNING(0, "生成中"),
    SUCCESS(1, "已完成"),
    FAILED(2, "生成失败");

    private final Integer code;
    private final String desc;

    AiTaskStatusVO(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AiTaskStatusVO fromCode(Integer code) {
        if (null == code) {
            return RUNNING;
        }
        for (AiTaskStatusVO value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return RUNNING;
    }
}
