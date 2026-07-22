package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.OutboxEventPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IOutboxEventDao {

    @Insert("""
            INSERT INTO outbox_event(event_id, event_type, aggregate_id, topic, payload, status, retry_count, next_retry_at, published_at, last_error, create_time, update_time)
            VALUES(#{eventId}, #{eventType}, #{aggregateId}, #{topic}, #{payload}, #{status}, #{retryCount}, #{nextRetryAt}, #{publishedAt}, #{lastError}, #{createTime}, #{updateTime})
            """)
    int insert(OutboxEventPO po);

    @Select("""
            SELECT event_id, event_type, aggregate_id, topic, payload, status, retry_count, next_retry_at, published_at, last_error, create_time, update_time
            FROM outbox_event
            WHERE status IN ('NEW', 'RETRYING')
              AND next_retry_at <= NOW()
            ORDER BY create_time ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEventPO> claimPublishable(@Param("limit") int limit);

    @Update("""
            UPDATE outbox_event
            SET status = 'PUBLISHED', published_at = NOW(), update_time = NOW()
            WHERE event_id = #{eventId}
            """)
    int markPublished(@Param("eventId") Long eventId);

    @Update("""
            UPDATE outbox_event
            SET status = 'RETRYING', retry_count = IFNULL(retry_count, 0) + 1,
                next_retry_at = #{nextRetryAt}, last_error = #{lastError}, update_time = NOW()
            WHERE event_id = #{eventId}
            """)
    int scheduleRetry(@Param("eventId") Long eventId, @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("lastError") String lastError);

    @Update("""
            UPDATE outbox_event
            SET status = 'FAILED', last_error = #{lastError}, update_time = NOW()
            WHERE event_id = #{eventId}
            """)
    int markFailed(@Param("eventId") Long eventId, @Param("lastError") String lastError);
}
