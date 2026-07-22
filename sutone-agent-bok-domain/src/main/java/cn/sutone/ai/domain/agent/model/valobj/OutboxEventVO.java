package cn.sutone.ai.domain.agent.model.valobj;

import lombok.Getter;

/**
 * Outbox 事件状态
 */
@Getter
public enum OutboxEventVO {

    NEW("NEW", "待投递"),
    RETRYING("RETRYING", "重试中"),
    PUBLISHED("PUBLISHED", "已投递"),
    FAILED("FAILED", "投递失败");

    private final String code;
    private final String desc;

    OutboxEventVO(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
