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
    FAILED(2, "生成失败"),
    RETRYING(4, "重试中");

    /** Compile-time constants for use in MyBatis annotation strings */
    public static final int CODE_RUNNING = 0;
    public static final int CODE_SUCCESS = 1;
    public static final int CODE_FAILED = 2;
    public static final int CODE_PENDING = 3;
    public static final int CODE_RETRYING = 4;
    /** "3,4" for SQL IN clause — PENDING and RETRYING are both claimable */
    public static final String CLAIMABLE_STATUSES = CODE_PENDING + "," + CODE_RETRYING;

    private final Integer code;
    private final String desc;

    AiTaskStatusVO(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AiTaskStatusVO fromCode(Integer code) {
        if (null == code) {
            return null;
        }
        for (AiTaskStatusVO value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
