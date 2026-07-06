package cn.sutone.ai.domain.demo.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Demo 状态值对象
 */
@Getter
@AllArgsConstructor
public enum DemoStatusVO {

    INIT(0, "初始化"),
    ENABLED(1, "启用"),
    DISABLED(2, "停用");

    private final Integer code;
    private final String desc;
}
