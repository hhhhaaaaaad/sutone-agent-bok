package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.ArticlePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章 DAO
 */
@Mapper
public interface IArticleDao {

    @Select("SELECT IFNULL(MAX(id), 0) + 1 FROM article")
    Long nextArticleId();

    @Insert("""
            INSERT INTO article(id, draft_id, author_id, title, content_md, content_html, summary, cover_url, status, publish_time, is_deleted)
            VALUES(#{id}, #{draftId}, #{authorId}, #{title}, #{contentMd}, #{contentHtml}, #{summary}, #{coverUrl}, #{status}, #{publishTime}, #{isDeleted})
            """)
    int insert(ArticlePO articlePO);

    @Insert("""
            UPDATE article
            SET title = #{title},
                content_md = #{contentMd},
                summary = #{summary},
                cover_url = #{coverUrl},
                status = #{status}
            WHERE id = #{id}
            """)
    int update(ArticlePO articlePO);

    @Select("""
            SELECT a.id, a.draft_id, a.author_id, a.title, a.content_md, a.content_html,
                   a.summary, a.cover_url, a.status, a.publish_time, a.is_deleted,
                   a.create_time, a.update_time,
                   u.username AS author_name, u.avatar_url
            FROM article a
            LEFT JOIN user u ON a.author_id = u.id
            WHERE a.id = #{articleId} AND a.is_deleted = 0
            """)
    ArticlePO queryByArticleId(@Param("articleId") Long articleId);

    @Select("""
            SELECT a.id, a.draft_id, a.author_id, a.title, a.content_md, a.content_html,
                   a.summary, a.cover_url, a.status, a.publish_time, a.is_deleted,
                   a.create_time, a.update_time,
                   u.username AS author_name, u.avatar_url
            FROM article a
            LEFT JOIN user u ON a.author_id = u.id
            WHERE a.draft_id = #{draftId} AND a.is_deleted = 0
            LIMIT 1
            """)
    ArticlePO queryByDraftId(@Param("draftId") Long draftId);

    @Select("""
            <script>
            SELECT a.id, a.draft_id, a.author_id, a.title, a.content_md, a.content_html,
                   a.summary, a.cover_url, a.status, a.publish_time, a.is_deleted,
                   a.create_time, a.update_time,
                   u.username AS author_name, u.avatar_url
            FROM article a
            LEFT JOIN user u ON a.author_id = u.id
            WHERE a.is_deleted = 0
            <if test="userId != null"> AND a.author_id = #{userId}</if>
            <if test="keyword != null and keyword != ''"> AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.title REGEXP #{keyword})</if>
            ORDER BY a.publish_time DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<ArticlePO> queryPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize,
                               @Param("userId") Long userId, @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM article
            WHERE is_deleted = 0
            <if test="userId != null"> AND author_id = #{userId}</if>
            <if test="keyword != null and keyword != ''"> AND (title LIKE CONCAT('%', #{keyword}, '%') OR title REGEXP #{keyword})</if>
            </script>
            """)
    Integer countPage(@Param("userId") Long userId, @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT a.id, a.draft_id, a.author_id, a.title, a.content_md, a.content_html,
                   a.summary, a.cover_url, a.status, a.publish_time, a.is_deleted,
                   a.create_time, a.update_time,
                   u.username AS author_name, u.avatar_url
            FROM article a
            LEFT JOIN user u ON a.author_id = u.id
            WHERE a.is_deleted = 0 AND a.id &lt; #{cursor}
            <if test="userId != null"> AND a.author_id = #{userId}</if>
            <if test="keyword != null and keyword != ''"> AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.title REGEXP #{keyword})</if>
            ORDER BY a.id DESC
            LIMIT #{pageSize}
            </script>
            """)
    List<ArticlePO> queryPageCursor(@Param("cursor") Long cursor, @Param("pageSize") Integer pageSize,
                                     @Param("userId") Long userId, @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT a.id, a.draft_id, a.author_id, a.title, a.content_md, a.content_html,
                   a.summary, a.cover_url, a.status, a.publish_time, a.is_deleted,
                   a.create_time, a.update_time,
                   u.username AS author_name, u.avatar_url
            FROM article a
            LEFT JOIN user u ON a.author_id = u.id
            WHERE a.is_deleted = 0 AND a.id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
                #{id}
            </foreach>
            ORDER BY a.publish_time DESC
            </script>
            """)
    List<ArticlePO> queryByIds(@Param("ids") List<Long> ids);
}
