package cn.sutone.ai.domain.agent.service.chat;

import cn.sutone.ai.domain.agent.adapter.repository.IChatMessageRepository;
import cn.sutone.ai.domain.agent.model.entity.ChatCommandEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.sutone.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.sutone.ai.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import cn.sutone.ai.domain.agent.service.IChatService;
import cn.sutone.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatService implements IChatService {

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private IChatMessageRepository chatMessageRepository;

    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    @Override
    public List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList() {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();

        List<AiAgentConfigTableVO.Agent> agentList = new ArrayList<>();
        if (null != tables) {
            for (AiAgentConfigTableVO vo : tables.values()) {
                if (null != vo.getAgent()) {
                    agentList.add(vo.getAgent());
                }
            }
        }

        return agentList;
    }

    @Override
    public String createSession(String agentId, String userId) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Session session = runner.sessionService().createSession(appName, userId)
                .blockingGet();
        
        String sessionId = session.id();
        // Update cache so subsequent handleMessage calls without sessionId can use this new session
        String cacheKey = userId + "_" + agentId;
        userSessions.put(cacheKey, sessionId);
        
        return sessionId;
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String message) {

        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        String cacheKey = userId + "_" + agentId;
        String sessionId = userSessions.get(cacheKey);
        if (sessionId == null) {
            sessionId = createSession(agentId, userId);
            // Session 恢复：从 DB 加载最近对话前缀
            recoverHistoryContext(userId, agentId, message, aiAgentRegisterVO, sessionId);
        }

        return handleMessage(agentId, userId, sessionId, message);
    }

    /** 重启后从 chat_message 恢复上下文（按 userId+agentId 过滤） */
    private void recoverHistoryContext(String userId, String agentId, String currentMessage,
                                       AiAgentRegisterVO vo, String newSessionId) {
        try {
            Long uid = null;
            try { uid = Long.parseLong(userId); } catch (NumberFormatException ignored) {}
            if (uid == null) return;

            List<String> history = chatMessageRepository.getLastMessagesByUserAgent(uid, agentId, 20);
            if (history.isEmpty()) return;

            InMemoryRunner runner = vo.getRunner();
            // 将历史消息前缀拼入新 session 的 context
            String prefix = "【历史对话上下文】\n" + String.join("\n", history) + "\n\n【当前消息】\n";
            Content prefixContent = Content.fromParts(Part.fromText(prefix));
            runner.runAsync(userId, newSessionId, prefixContent).blockingForEach(e -> {});
            log.info("Session 恢复: userId={} agentId={} 恢复 {} 条历史", userId, agentId, history.size());
        } catch (Exception e) {
            log.warn("Session 恢复失败 userId={} agentId={}: {}", userId, agentId, e.getMessage());
        }
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {

        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        // 持久化用户消息
        persistMessage(userId, sessionId, agentId, "user", message);

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Content userMsg = Content.fromParts(Part.fromText(message));
        Flowable<Event> events = runner.runAsync(userId, sessionId, userMsg);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        // 持久化 AI 回复
        String response = String.join("\n", outputs);
        if (!response.isBlank()) {
            persistMessage(userId, sessionId, agentId, "assistant", response);
        }

        return outputs;
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        // 持久化用户消息
        persistMessage(userId, sessionId, agentId, "user", message);

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Content userMsg = Content.fromParts(Part.fromText(message));
        RunConfig runConfig = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE).build();

        // 收集 AI 回复并持久化
        StringBuilder aiResponse = new StringBuilder();
        return runner.runAsync(userId, sessionId, userMsg, runConfig)
                .doOnNext(event -> {
                    String content = event.stringifyContent();
                    if (content != null && !content.isBlank()) {
                        aiResponse.append(content);
                    }
                })
                .doOnComplete(() -> {
                    String response = aiResponse.toString();
                    if (!response.isBlank()) {
                        persistMessage(userId, sessionId, agentId, "assistant", response);
                    }
                });
    }

    /** 持久化对话消息，不影响主流程 */
    private void persistMessage(String userId, String sessionId, String agentId, String role, String content) {
        try {
            Long uid = null;
            try { uid = Long.parseLong(userId); } catch (NumberFormatException ignored) {}
            chatMessageRepository.save(uid, sessionId, agentId, role, content);
        } catch (Exception e) {
            log.warn("对话消息持久化失败 sessionId={} role={}: {}", sessionId, role, e.getMessage());
        }
    }

    @Override
    public List<String> handleMessage(ChatCommandEntity chatCommandEntity) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(chatCommandEntity.getAgentId());

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        List<Part> parts = new ArrayList<>();

        List<ChatCommandEntity.Content.Text> texts = chatCommandEntity.getTexts();
        if (null != texts && !texts.isEmpty()) {
            for (ChatCommandEntity.Content.Text text : texts) {
                parts.add(Part.fromText(text.getMessage()));
            }
        }

        List<ChatCommandEntity.Content.File> files = chatCommandEntity.getFiles();
        if (null != files && !files.isEmpty()) {
            for (ChatCommandEntity.Content.File file : files) {
                parts.add(Part.fromUri(file.getFileUri(), file.getMimeType()));
            }
        }

        List<ChatCommandEntity.Content.InlineData> inlineDatas = chatCommandEntity.getInlineDatas();
        if (null != inlineDatas && !inlineDatas.isEmpty()) {
            for (ChatCommandEntity.Content.InlineData inlineData : inlineDatas) {
                parts.add(Part.fromBytes(inlineData.getBytes(), inlineData.getMimeType()));
            }
        }

        Content content = Content.builder().role("user").parts(parts).build();

        // 获取运行体
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Flowable<Event> events = runner.runAsync(chatCommandEntity.getUserId(), chatCommandEntity.getSessionId(), content);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        return outputs;
    }

}
