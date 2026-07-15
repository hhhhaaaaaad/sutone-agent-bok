package cn.sutone.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ICommentLikeDao {

    @Insert("INSERT IGNORE INTO comment_like(comment_id, user_id) VALUES(#{commentId}, #{userId})")
    int insert(@Param("commentId") Long commentId, @Param("userId") Long userId);

    @Delete("DELETE FROM comment_like WHERE comment_id = #{commentId} AND user_id = #{userId}")
    int delete(@Param("commentId") Long commentId, @Param("userId") Long userId);

    @Select("SELECT COUNT(1) > 0 FROM comment_like WHERE comment_id = #{commentId} AND user_id = #{userId}")
    boolean exists(@Param("commentId") Long commentId, @Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM comment_like WHERE comment_id = #{commentId}")
    int countByCommentId(@Param("commentId") Long commentId);

    @Select("SELECT user_id FROM comment_like WHERE comment_id = #{commentId}")
    List<Long> findUserIdsByCommentId(@Param("commentId") Long commentId);
}
