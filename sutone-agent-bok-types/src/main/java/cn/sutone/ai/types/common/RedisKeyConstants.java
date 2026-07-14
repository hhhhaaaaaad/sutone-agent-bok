package cn.sutone.ai.types.common;

public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    /** AI 接口限流 per user */
    public static final String RATE_LIMIT_PREFIX = "ai:limit:";

    /** 文章详情缓存 */
    public static final String ARTICLE_DETAIL_PREFIX = "article:detail:";

    /** 文章列表缓存 */
    public static final String ARTICLE_PAGE_PREFIX = "article:page:";

    /** 文章缓存互斥锁（防击穿） */
    public static final String ARTICLE_LOCK_PREFIX = "article:lock:";

    /** AI 任务防重复提交锁 */
    public static final String AI_TASK_LOCK_PREFIX = "ai:task:lock:";

    /** 文章点赞用户集合 */
    public static final String ARTICLE_LIKE_PREFIX = "article:like:";

    /** 文章被收藏用户集合 */
    public static final String ARTICLE_FAVORITE_PREFIX = "article:favorite:";

    /** 用户收藏文章列表 */
    public static final String USER_FAVORITE_PREFIX = "user:favorite:";

    /** 用户点赞文章列表 */
    public static final String USER_LIKE_PREFIX = "user:like:";

    /** 热门排行榜 */
    public static final String LEADERBOARD_VIEW_PREFIX = "leaderboard:view:";
}
