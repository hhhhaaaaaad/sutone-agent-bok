package cn.sutone.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IUserFollowDao {

    @Insert("INSERT INTO user_follow(follower_id, followee_id) VALUES(#{followerId}, #{followeeId})")
    int insert(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Delete("DELETE FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Select("SELECT COUNT(1) > 0 FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    boolean exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Select("SELECT followee_id FROM user_follow WHERE follower_id = #{userId}")
    List<Long> findFollowingIds(@Param("userId") Long userId);

    @Select("SELECT follower_id FROM user_follow WHERE followee_id = #{userId}")
    List<Long> findFollowerIds(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM user_follow WHERE followee_id = #{userId}")
    int countFollowers(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM user_follow WHERE follower_id = #{userId}")
    int countFollowing(@Param("userId") Long userId);
}
