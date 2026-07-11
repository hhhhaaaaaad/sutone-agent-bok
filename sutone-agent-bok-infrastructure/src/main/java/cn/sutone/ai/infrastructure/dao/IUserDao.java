package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.UserPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 DAO
 */
@Mapper
public interface IUserDao {

    @Select("""
            SELECT id, username, password_hash, nickname, avatar_url, is_deleted, create_time, update_time
            FROM user
            WHERE id = #{id} AND is_deleted = 0
            """)
    UserPO selectById(@Param("id") Long id);

    @Select("""
            SELECT id, username, password_hash, nickname, avatar_url, is_deleted, create_time, update_time
            FROM user
            WHERE username = #{username} AND is_deleted = 0
            """)
    UserPO selectByUsername(@Param("username") String username);

    @Insert("""
            INSERT INTO user(username, password_hash, nickname, avatar_url, is_deleted)
            VALUES(#{username}, #{passwordHash}, #{nickname}, #{avatarUrl}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserPO userPO);
}
