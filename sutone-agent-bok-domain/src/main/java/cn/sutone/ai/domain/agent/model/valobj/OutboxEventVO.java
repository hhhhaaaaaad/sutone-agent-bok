package cn.sutone.ai.domain.agent.model.valobj;

import lombok.Getter;

/**
 * Outbox 事件状态
 */
@Getter
public enum OutboxEventVO {

    NEW("NEW", "待投递"),
    RETRYING("RETRYING", "重试中"),
    SENDING("SENDING", "投递中"),
    PUBLISHED("PUBLISHED", "已投递"),
    FAILED("FAILED", "投递失败");

    /** Compile-time constants for use in MyBatis annotation strings */
    public static final String CODE_NEW = "NEW";
    public static final String CODE_RETRYING = "RETRYING";
    public static final String CODE_SENDING = "SENDING";
    public static final String CODE_PUBLISHED = "PUBLISHED";
    public static final String CODE_FAILED = "FAILED";
    /** "'NEW','RETRYING'" for SQL IN clause */
    public static final String PUBLISHABLE_STATUSES = "'" + CODE_NEW + "','" + CODE_RETRYING + "'";
    /** "'NEW','RETRYING','SENDING'" for SQL IN clause */
    public static final String PENDING_OR_SENDING_STATUSES = "'" + CODE_NEW + "','" + CODE_RETRYING + "','" + CODE_SENDING + "'";

    private final String code;
    private final String desc;

    OutboxEventVO(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
