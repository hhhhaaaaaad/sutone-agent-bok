package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.ArticleCommentPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface IArticleCommentDao {

    @Insert("""
            INSERT INTO article_comment(article_id, user_id, parent_id, content)
            VALUES(#{articleId}, #{userId}, #{parentId}, #{content})
            """)
    int insert(ArticleCommentPO po);

    @Update("UPDATE article_comment SET is_deleted = 1 WHERE id = #{id}")
    int logicalDelete(@Param("id") Long id);

    @Select("""
            SELECT c.id, c.article_id, c.user_id, c.parent_id, c.content,
                   c.like_count, c.is_deleted, c.create_time,
                   u.username AS author_name, u.avatar_url
            FROM article_comment c
            LEFT JOIN user u ON c.user_id = u.id
            WHERE c.article_id = #{articleId} AND c.parent_id IS NULL AND c.is_deleted = 0
            ORDER BY c.create_time DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<ArticleCommentPO> queryByArticleId(@Param("articleId") Long articleId,
                                            @Param("offset") int offset,
                                            @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT c.id, c.article_id, c.user_id, c.parent_id, c.content,
                   c.like_count, c.is_deleted, c.create_time,
                   u.username AS author_name, u.avatar_url
            FROM article_comment c
            LEFT JOIN user u ON c.user_id = u.id
            WHERE c.parent_id IN
            <foreach collection='parentIds' item='pid' open='(' separator=',' close=')'>
                #{pid}
            </foreach>
            AND c.is_deleted = 0
            ORDER BY c.create_time ASC
            LIMIT #{limit}
            </script>
            """)
    List<ArticleCommentPO> queryByParentIds(@Param("parentIds") List<Long> parentIds,
                                            @Param("limit") int limit);

    @Select("SELECT COUNT(1) FROM article_comment WHERE article_id = #{articleId} AND parent_id IS NULL AND is_deleted = 0")
    int countByArticleId(@Param("articleId") Long articleId);

    @Select("SELECT id, article_id, user_id, parent_id, content, like_count, is_deleted, create_time FROM article_comment WHERE id = #{id}")
    ArticleCommentPO selectById(@Param("id") Long id);

    @Update("UPDATE article_comment SET like_count = #{likeCount} WHERE id = #{id}")
    int updateLikeCount(@Param("id") Long id, @Param("likeCount") int likeCount);

    @Select("""
            SELECT DATE(c.create_time) as date, COUNT(1) as cnt
            FROM article_comment c JOIN article a ON c.article_id = a.id
            WHERE a.author_id = #{userId} AND c.create_time >= #{since}
            GROUP BY DATE(c.create_time) ORDER BY date
            """)
    List<Map<String, Object>> countDailyByAuthor(@Param("userId") Long userId, @Param("since") String since);
}
