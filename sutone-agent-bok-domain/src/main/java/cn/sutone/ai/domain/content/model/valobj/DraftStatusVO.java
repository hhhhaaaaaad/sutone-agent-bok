package cn.sutone.ai.domain.content.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 草稿状态值对象
 */
@Getter
@AllArgsConstructor
public enum DraftStatusVO {

    EDITING(0, "编辑中"),
    PUBLISHED(1, "已发布"),
    DISCARDED(2, "已废弃");

    private final Integer code;
    private final String desc;
}
