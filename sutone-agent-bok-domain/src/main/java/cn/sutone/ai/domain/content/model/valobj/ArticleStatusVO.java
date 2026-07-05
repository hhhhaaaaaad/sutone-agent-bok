package cn.sutone.ai.domain.content.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文章状态值对象
 */
@Getter
@AllArgsConstructor
public enum ArticleStatusVO {

    PUBLISHED(1, "正常可见"),
    OFFLINE(0, "已下线");

    private final Integer code;
    private final String desc;
}
