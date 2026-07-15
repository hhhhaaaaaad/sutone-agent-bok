package cn.sutone.ai.types.enums;

public enum NotificationType {

    NEW_LIKE("NEW_LIKE", "点赞了你的文章"),
    NEW_COMMENT("NEW_COMMENT", "评论了你的文章"),
    NEW_COMMENT_LIKE("NEW_COMMENT_LIKE", "点赞了你的评论"),
    NEW_FOLLOW("NEW_FOLLOW", "关注了你"),
    NEW_ARTICLE("NEW_ARTICLE", "发布了新文章");

    private final String code;
    private final String desc;

    NotificationType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
