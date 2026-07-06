package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.DraftPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 草稿 DAO
 */
@Mapper
public interface IDraftDao {

    @Select("SELECT IFNULL(MAX(id), 0) + 1 FROM draft")
    Long nextDraftId();

    @Insert("""
            INSERT INTO draft(id, user_id, title, content_md, summary, cover_url, status, is_deleted)
            VALUES(#{id}, #{userId}, #{title}, #{contentMd}, #{summary}, #{coverUrl}, #{status}, #{isDeleted})
            """)
    int insert(DraftPO draftPO);

    @Update("""
            UPDATE draft
            SET title = #{title},
                content_md = #{contentMd},
                summary = #{summary},
                cover_url = #{coverUrl},
                status = #{status},
                is_deleted = #{isDeleted}
            WHERE id = #{id}
            """)
    int update(DraftPO draftPO);

    @Select("""
            SELECT id, user_id, title, content_md, summary, cover_url, status, is_deleted, create_time, update_time
            FROM draft
            WHERE id = #{draftId} AND is_deleted = 0
            """)
    DraftPO queryById(@Param("draftId") Long draftId);

    @Select("""
            SELECT id, user_id, title, content_md, summary, cover_url, status, is_deleted, create_time, update_time
            FROM draft
            WHERE user_id = #{userId} AND is_deleted = 0
            ORDER BY update_time DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<DraftPO> queryPage(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("pageSize") Integer pageSize);

    @Select("""
            SELECT COUNT(1)
            FROM draft
            WHERE user_id = #{userId} AND is_deleted = 0
            """)
    Integer countByUserId(@Param("userId") Long userId);
}
