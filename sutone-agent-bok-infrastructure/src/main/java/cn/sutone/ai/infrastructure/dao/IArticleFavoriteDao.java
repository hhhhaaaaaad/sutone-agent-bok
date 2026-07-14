package cn.sutone.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章收藏 DAO
 */
@Mapper
public interface IArticleFavoriteDao {

    @Insert("INSERT IGNORE INTO article_favorite(article_id, user_id) VALUES(#{articleId}, #{userId})")
    int insert(@Param("articleId") Long articleId, @Param("userId") Long userId);

    @Delete("DELETE FROM article_favorite WHERE article_id = #{articleId} AND user_id = #{userId}")
    int delete(@Param("articleId") Long articleId, @Param("userId") Long userId);

    @Select("SELECT COUNT(1) > 0 FROM article_favorite WHERE article_id = #{articleId} AND user_id = #{userId}")
    boolean exists(@Param("articleId") Long articleId, @Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM article_favorite WHERE article_id = #{articleId}")
    int countByArticleId(@Param("articleId") Long articleId);

    @Select("SELECT user_id FROM article_favorite WHERE article_id = #{articleId}")
    List<Long> findUserIdsByArticleId(@Param("articleId") Long articleId);

    @Select("SELECT article_id FROM article_favorite WHERE user_id = #{userId}")
    List<Long> findArticleIdsByUserId(@Param("userId") Long userId);
}
