package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.agent.adapter.repository.IChatMessageRepository;
import cn.sutone.ai.infrastructure.dao.IChatMessageDao;
import cn.sutone.ai.infrastructure.dao.po.ChatMessagePO;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ChatMessageRepository implements IChatMessageRepository {

    @Resource
    private IChatMessageDao chatMessageDao;

    @Override
    public void save(Long userId, String sessionId, String agentId, String role, String content) {
        ChatMessagePO po = ChatMessagePO.builder()
                .userId(userId)
                .sessionId(sessionId)
                .agentId(agentId)
                .role(role)
                .content(content)
                .build();
        chatMessageDao.insert(po);
    }

    @Override
    public List<String> getLastMessages(String sessionId, int limit) {
        List<ChatMessagePO> messages = chatMessageDao.selectLastN(sessionId, limit);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(messages);
        return messages.stream()
                .map(m -> "[" + m.getRole() + "]: " + m.getContent())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getLastMessagesByUserAgent(Long userId, String agentId, int limit) {
        List<ChatMessagePO> messages = chatMessageDao.selectLastNByUserAgent(userId, agentId, limit);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(messages);
        return messages.stream()
                .map(m -> "[" + m.getRole() + "]: " + m.getContent())
                .collect(Collectors.toList());
    }
}
