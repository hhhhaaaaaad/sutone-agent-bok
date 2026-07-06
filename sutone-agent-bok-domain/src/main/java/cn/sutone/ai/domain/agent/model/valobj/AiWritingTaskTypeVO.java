package cn.sutone.ai.domain.agent.model.valobj;

import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import lombok.Getter;

/**
 * AI 写作任务类型
 */
@Getter
public enum AiWritingTaskTypeVO {

    GENERATE_OUTLINE("GENERATE_OUTLINE", "生成大纲"),
    GENERATE_BODY("GENERATE_BODY", "续写正文"),
    POLISH_TEXT("POLISH_TEXT", "润色改写"),
    SUMMARIZE("SUMMARIZE", "生成摘要");

    private final String code;
    private final String desc;

    AiWritingTaskTypeVO(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AiWritingTaskTypeVO fromCode(String code) {
        if (null == code || code.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "AI 写作任务类型不能为空");
        }
        for (AiWritingTaskTypeVO value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不支持的 AI 写作任务类型");
    }
}
