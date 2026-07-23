package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.domain.agent.model.valobj.AiTaskStatusVO;
import cn.sutone.ai.infrastructure.dao.po.AiTaskPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
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

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO ai_task(user_id, draft_id, task_type, prompt_payload, enable_illustration, "
            + "response_content, status, error_msg, is_deleted, started_at, heartbeat_at, retry_count, next_retry_at, worker_id) "
            + "VALUES(#{userId}, #{draftId}, #{taskType}, #{promptPayload}, #{enableIllustration}, #{responseContent}, "
            + "#{status}, #{errorMsg}, #{isDeleted}, #{startedAt}, #{heartbeatAt}, #{retryCount}, #{nextRetryAt}, #{workerId})")
    int insert(AiTaskPO aiTaskPO);

    @Update("UPDATE ai_task "
            + "SET response_content = #{responseContent}, status = #{status}, error_msg = #{errorMsg}, "
            + "started_at = #{startedAt}, heartbeat_at = #{heartbeatAt}, retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, worker_id = #{workerId}, is_deleted = #{isDeleted}, update_time = NOW() "
            + "WHERE id = #{id}")
    int update(AiTaskPO aiTaskPO);

    @Update("UPDATE ai_task "
            + "SET status = #{status}, started_at = NOW(), heartbeat_at = NOW(), "
            + "worker_id = #{workerId}, update_time = NOW() "
            + "WHERE id = #{taskId} "
            + "AND status IN (" + AiTaskStatusVO.CLAIMABLE_STATUSES + ") "
            + "AND (next_retry_at IS NULL OR next_retry_at <= NOW())")
    int claimTask(@Param("taskId") Long taskId, @Param("status") Integer status, @Param("workerId") String workerId);

    @Update("UPDATE ai_task "
            + "SET response_content = #{responseContent}, status = " + AiTaskStatusVO.CODE_SUCCESS + ", "
            + "error_msg = NULL, update_time = NOW() "
            + "WHERE id = #{taskId} AND status = " + AiTaskStatusVO.CODE_RUNNING)
    int markSuccess(@Param("taskId") Long taskId, @Param("responseContent") String responseContent);

    @Update("UPDATE ai_task "
            + "SET status = " + AiTaskStatusVO.CODE_FAILED + ", error_msg = #{errorMsg}, update_time = NOW() "
            + "WHERE id = #{taskId}")
    int markFailed(@Param("taskId") Long taskId, @Param("errorMsg") String errorMsg);

    @Update("UPDATE ai_task "
            + "SET status = " + AiTaskStatusVO.CODE_RETRYING + ", retry_count = IFNULL(retry_count, 0) + 1, "
            + "next_retry_at = DATE_ADD(NOW(), INTERVAL 30 SECOND), error_msg = #{errorMsg}, update_time = NOW() "
            + "WHERE id = #{taskId}")
    int markRetrying(@Param("taskId") Long taskId, @Param("errorMsg") String errorMsg);

    /** RetryableAgentException 专用：立即允许重试，不等退避延迟 */
    @Update("UPDATE ai_task "
            + "SET status = " + AiTaskStatusVO.CODE_RETRYING + ", retry_count = IFNULL(retry_count, 0) + 1, "
            + "next_retry_at = NOW(), error_msg = #{errorMsg}, update_time = NOW() "
            + "WHERE id = #{taskId}")
    int markRetryingImmediate(@Param("taskId") Long taskId, @Param("errorMsg") String errorMsg);

    @Update("UPDATE ai_task SET heartbeat_at = NOW() WHERE id = #{taskId}")
    int touchHeartbeat(@Param("taskId") Long taskId);

    @Select("SELECT id, user_id, draft_id, task_type, prompt_payload, enable_illustration, "
            + "response_content, status, error_msg, create_time, update_time, is_deleted, "
            + "started_at, heartbeat_at, retry_count, next_retry_at, worker_id "
            + "FROM ai_task WHERE id = #{taskId} AND is_deleted = 0")
    AiTaskPO queryById(@Param("taskId") Long taskId);

    @Select("SELECT id, user_id, draft_id, task_type, prompt_payload, enable_illustration, "
            + "response_content, status, error_msg, create_time, update_time, is_deleted, "
            + "started_at, heartbeat_at, retry_count, next_retry_at, worker_id "
            + "FROM ai_task WHERE draft_id = #{draftId} AND is_deleted = 0 "
            + "ORDER BY create_time DESC LIMIT #{limit}")
    List<AiTaskPO> queryByDraftId(@Param("draftId") Long draftId, @Param("limit") int limit);

    @Select("SELECT id, user_id, draft_id, task_type, prompt_payload, enable_illustration, "
            + "response_content, status, error_msg, create_time, update_time, is_deleted, "
            + "started_at, heartbeat_at, retry_count, next_retry_at, worker_id "
            + "FROM ai_task "
            + "WHERE status IN (" + AiTaskStatusVO.CODE_RUNNING + "," + AiTaskStatusVO.CODE_RETRYING + ") "
            + "AND heartbeat_at < #{timeout} AND is_deleted = 0 LIMIT #{limit}")
    List<AiTaskPO> findStaleRunning(@Param("timeout") LocalDateTime timeout, @Param("limit") int limit);
}
