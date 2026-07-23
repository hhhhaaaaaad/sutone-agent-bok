package cn.sutone.ai.domain.agent.adapter.repository;

import cn.sutone.ai.domain.agent.model.valobj.ChatMessageVO;

import java.util.List;

/**
 * 对话消息仓储接口
 */
public interface IChatMessageRepository {

    /** 保存一条消息 */
    void save(Long userId, String sessionId, String agentId, String role, String content);

    /** 获取会话最近 N 条消息 */
    List<String> getLastMessages(String sessionId, int limit);

    /** 按 userId + agentId 获取最近 N 条消息（Session 恢复用） */
    List<String> getLastMessagesByUserAgent(Long userId, String agentId, int limit);

    /** 按 userId + agentId 获取最近 N 条消息（供前端展示历史对话） */
    List<ChatMessageVO> getHistoryByUserAgent(Long userId, String agentId, int limit);

    /** 按 sessionId 获取最近 N 条消息（供前端展示历史对话） */
    List<ChatMessageVO> getHistoryBySessionId(String sessionId, int limit);
}
