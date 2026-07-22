package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.AiTaskPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 任务 DAO
 */
@Mapper
public interface IAiTaskDao {

    @Select("SELECT IFNULL(MAX(id), 0) + 1 FROM ai_task")
    Long nextTaskId();

    @Insert("""
            INSERT INTO ai_task(id, user_id, draft_id, task_type, prompt_payload, enable_illustration, response_content, status, error_msg, is_deleted, started_at, heartbeat_at, retry_count, next_retry_at, worker_id)
            VALUES(#{id}, #{userId}, #{draftId}, #{taskType}, #{promptPayload}, #{enableIllustration}, #{responseContent}, #{status}, #{errorMsg}, #{isDeleted}, #{startedAt}, #{heartbeatAt}, #{retryCount}, #{nextRetryAt}, #{workerId})
            """)
    int insert(AiTaskPO aiTaskPO);

    @Update("""
            UPDATE ai_task
            SET response_content = #{responseContent},
                status = #{status},
                error_msg = #{errorMsg},
                started_at = #{startedAt},
                heartbeat_at = #{heartbeatAt},
                retry_count = #{retryCount},
                next_retry_at = #{nextRetryAt},
                worker_id = #{workerId},
                is_deleted = #{isDeleted},
                update_time = NOW()
            WHERE id = #{id}
            """)
    int update(AiTaskPO aiTaskPO);

    @Update("""
            UPDATE ai_task
            SET status = #{status}, started_at = NOW(), heartbeat_at = NOW(),
                worker_id = #{workerId}, update_time = NOW()
            WHERE id = #{taskId}
              AND status IN (3, 4)
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
            """)
    int claimTask(@Param("taskId") Long taskId, @Param("status") Integer status, @Param("workerId") String workerId);

    @Update("""
            UPDATE ai_task
            SET response_content = #{responseContent}, status = 1, error_msg = NULL, update_time = NOW()
            WHERE id = #{taskId} AND status = 0
            """)
    int markSuccess(@Param("taskId") Long taskId, @Param("responseContent") String responseContent);

    @Update("""
            UPDATE ai_task
            SET status = 2, error_msg = #{errorMsg}, update_time = NOW()
            WHERE id = #{taskId}
            """)
    int markFailed(@Param("taskId") Long taskId, @Param("errorMsg") String errorMsg);

    @Update("""
            UPDATE ai_task
            SET status = 4, retry_count = IFNULL(retry_count, 0) + 1,
                next_retry_at = DATE_ADD(NOW(), INTERVAL 30 SECOND), error_msg = #{errorMsg}, update_time = NOW()
            WHERE id = #{taskId}
            """)
    int markRetrying(@Param("taskId") Long taskId, @Param("errorMsg") String errorMsg);

    @Update("""
            UPDATE ai_task
            SET heartbeat_at = NOW()
            WHERE id = #{taskId}
            """)
    int touchHeartbeat(@Param("taskId") Long taskId);

    @Select("""
            SELECT id, user_id, draft_id, task_type, prompt_payload, enable_illustration,
                   response_content, status, error_msg, create_time, update_time, is_deleted,
                   started_at, heartbeat_at, retry_count, next_retry_at, worker_id
            FROM ai_task
            WHERE id = #{taskId} AND is_deleted = 0
            """)
    AiTaskPO queryById(@Param("taskId") Long taskId);

    @Select("""
            SELECT id, user_id, draft_id, task_type, prompt_payload, enable_illustration,
                   response_content, status, error_msg, create_time, update_time, is_deleted,
                   started_at, heartbeat_at, retry_count, next_retry_at, worker_id
            FROM ai_task
            WHERE draft_id = #{draftId} AND is_deleted = 0
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<AiTaskPO> queryByDraftId(@Param("draftId") Long draftId, @Param("limit") int limit);

    @Select("""
            SELECT id, user_id, draft_id, task_type, prompt_payload, enable_illustration,
                   response_content, status, error_msg, create_time, update_time, is_deleted,
                   started_at, heartbeat_at, retry_count, next_retry_at, worker_id
            FROM ai_task
            WHERE status = 0
              AND heartbeat_at < #{timeout}
              AND is_deleted = 0
            LIMIT #{limit}
            """)
    List<AiTaskPO> findStaleRunning(@Param("timeout") LocalDateTime timeout, @Param("limit") int limit);
}
