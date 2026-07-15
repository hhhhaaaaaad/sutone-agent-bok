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

    @Select("SELECT like_count FROM article_meta WHERE article_id = #{articleId}")
    Integer selectLikeCount(@Param("articleId") Long articleId);

    @Select("SELECT favorite_count FROM article_meta WHERE article_id = #{articleId}")
    Integer selectFavoriteCount(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_meta
            SET word_count = #{wordCount},
                tags = #{tags}
            WHERE article_id = #{articleId}
            """)
    int updateByArticleId(ArticleMetaPO articleMetaPO);

    @Update("""
            UPDATE article_meta
            SET like_count = like_count + 1
            WHERE article_id = #{articleId}
            """)
    int increaseLikeCount(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_meta
            SET like_count = GREATEST(like_count - 1, 0)
            WHERE article_id = #{articleId}
            """)
    int decreaseLikeCount(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_meta
            SET favorite_count = favorite_count + 1
            WHERE article_id = #{articleId}
            """)
    int increaseFavoriteCount(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_meta
            SET favorite_count = GREATEST(favorite_count - 1, 0)
            WHERE article_id = #{articleId}
            """)
    int decreaseFavoriteCount(@Param("articleId") Long articleId);
}
