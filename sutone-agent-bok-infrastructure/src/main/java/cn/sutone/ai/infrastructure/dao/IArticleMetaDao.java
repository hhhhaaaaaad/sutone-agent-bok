package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.ArticleMetaPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 文章元数据 DAO
 */
@Mapper
public interface IArticleMetaDao {

    @Insert("""
            INSERT INTO article_meta(article_id, word_count, view_count, like_count, favorite_count, tags)
            VALUES(#{articleId}, #{wordCount}, #{viewCount}, #{likeCount}, #{favoriteCount}, #{tags})
            """)
    int insert(ArticleMetaPO articleMetaPO);

    @Select("""
            SELECT id, article_id, word_count, view_count, like_count, favorite_count, tags, create_time, update_time
            FROM article_meta
            WHERE article_id = #{articleId}
            """)
    ArticleMetaPO queryByArticleId(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_meta
            SET view_count = view_count + 1
            WHERE article_id = #{articleId}
            """)
    int increaseViewCount(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_meta
            SET word_count = #{wordCount},
                tags = #{tags}
            WHERE article_id = #{articleId}
            """)
    int updateByArticleId(ArticleMetaPO articleMetaPO);
}
