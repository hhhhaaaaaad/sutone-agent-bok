package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.AiTaskPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI 任务 DAO
 */
@Mapper
public interface IAiTaskDao {

    @Select("SELECT IFNULL(MAX(id), 0) + 1 FROM ai_task")
    Long nextTaskId();

    @Insert("""
            INSERT INTO ai_task(id, user_id, draft_id, task_type, prompt_payload, response_content, status, error_msg, is_deleted)
            VALUES(#{id}, #{userId}, #{draftId}, #{taskType}, #{promptPayload}, #{responseContent}, #{status}, #{errorMsg}, #{isDeleted})
            """)
    int insert(AiTaskPO aiTaskPO);

    @Update("""
            UPDATE ai_task
            SET response_content = #{responseContent},
                status = #{status},
                error_msg = #{errorMsg},
                is_deleted = #{isDeleted}
            WHERE id = #{id}
            """)
    int update(AiTaskPO aiTaskPO);

    @Select("""
            SELECT id, user_id, draft_id, task_type, prompt_payload, response_content, status, error_msg, create_time, update_time, is_deleted
            FROM ai_task
            WHERE id = #{taskId} AND is_deleted = 0
            """)
    AiTaskPO queryById(@Param("taskId") Long taskId);

    @Select("""
            SELECT id, user_id, draft_id, task_type, prompt_payload, response_content, status, error_msg, create_time, update_time, is_deleted
            FROM ai_task
            WHERE draft_id = #{draftId} AND is_deleted = 0
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<AiTaskPO> queryByDraftId(@Param("draftId") Long draftId, @Param("limit") int limit);
}
