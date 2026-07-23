package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.ChatMessagePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IChatMessageDao {

    @Insert("""
            INSERT INTO chat_message(user_id, session_id, agent_id, role, content)
            VALUES(#{userId}, #{sessionId}, #{agentId}, #{role}, #{content})
            """)
    int insert(ChatMessagePO po);

    @Select("""
            SELECT id, user_id, session_id, agent_id, role, content, create_time
            FROM chat_message
            WHERE session_id = #{sessionId}
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<ChatMessagePO> selectLastN(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Select("""
            SELECT id, user_id, session_id, agent_id, role, content, create_time
            FROM chat_message
            WHERE user_id = #{userId} AND agent_id = #{agentId}
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<ChatMessagePO> selectLastNByUserAgent(@Param("userId") Long userId, @Param("agentId") String agentId, @Param("limit") int limit);

    @Select("""
            SELECT id, user_id, session_id, agent_id, role, content, create_time
            FROM chat_message
            WHERE user_id = #{userId} AND agent_id = #{agentId}
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    List<ChatMessagePO> selectHistoryByUserAgent(@Param("userId") Long userId, @Param("agentId") String agentId, @Param("limit") int limit);

    @Select("""
            SELECT id, user_id, session_id, agent_id, role, content, create_time
            FROM chat_message
            WHERE session_id = #{sessionId}
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    List<ChatMessagePO> selectHistoryBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);
}
