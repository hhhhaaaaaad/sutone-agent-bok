package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.agent.service.IChatService;
import cn.sutone.ai.domain.agent.service.memory.MemoryManager;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import com.alibaba.fastjson.JSONObject;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多轮对话写作 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/writing/chat")
@CrossOrigin(origins = "*")
public class WritingChatController {

    private static final String CHAT_AGENT_ID = "300004";

    @Resource
    private IChatService chatService;

    @Resource
    private MemoryManager memoryManager;

    /** 从 JWT 获取当前用户 ID */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }

    /** 创建写作会话 */
    @PostMapping("/create_session")
    public Response<String> createSession(@RequestParam(value = "draftId", required = false) Long draftId) {
        try {
            Long userId = getCurrentUserId();
            String uid = userId != null ? String.valueOf(userId) : "anonymous";
            String sessionId = chatService.createSession(CHAT_AGENT_ID, uid);
            log.info("写作会话创建成功 sessionId={} userId={} draftId={}", sessionId, userId, draftId);
            return Response.<String>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(sessionId)
                    .build();
        } catch (AppException e) {
            log.error("创建写作会话失败", e);
            return Response.<String>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("创建写作会话失败", e);
            return Response.<String>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /** 流式对话 */
    @PostMapping("/stream")
    public ResponseBodyEmitter chatStream(@RequestBody Map<String, Object> body) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(5 * 60 * 1000L);
        String sessionId = (String) body.get("sessionId");
        String message = (String) body.get("message");
        Long userId = getCurrentUserId();
        String uid = userId != null ? String.valueOf(userId) : "anonymous";

        try {
            String enrichedMessage = enrichWithMemory(userId, message);
            Flowable<Event> events = chatService.handleMessageStream(CHAT_AGENT_ID, uid, sessionId, enrichedMessage);

            java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);

            events.subscribe(
                    event -> {
                        if (completed.get()) return;
                        try {
                            String content = event.stringifyContent();
                            if (content == null || content.isEmpty()) return;

                            com.alibaba.fastjson.JSONObject msg = new com.alibaba.fastjson.JSONObject();
                            com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
                            chunk.put("type", "token");
                            chunk.put("content", content);
                            msg.put("chunk", chunk);
                            emitter.send(msg.toJSONString() + "\n");
                        } catch (Exception e) {
                            // emitter already completed, ignore
                        }
                    },
                    error -> {
                        if (completed.compareAndSet(false, true)) {
                            emitter.completeWithError(error);
                        }
                    },
                    () -> {
                        if (completed.compareAndSet(false, true)) {
                            try {
                                com.alibaba.fastjson.JSONObject done = new com.alibaba.fastjson.JSONObject();
                                com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
                                chunk.put("type", "done");
                                done.put("chunk", chunk);
                                emitter.send(done.toJSONString() + "\n");
                            } catch (Exception ignored) {}
                            emitter.complete();
                        }
                    }
            );

            emitter.onCompletion(() -> completed.set(true));
            emitter.onTimeout(() -> completed.set(true));
            emitter.onError(e -> completed.set(true));
        } catch (Exception e) {
            log.error("流式对话失败", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** 触发记忆抽取（用户保存文章后调用） */
    @PostMapping("/save")
    public Response<String> save(@RequestBody Map<String, Object> body) {
        try {
            String sessionId = (String) body.get("sessionId");
            Long userId = getCurrentUserId();

            // 异步触发记忆抽取
            if (userId != null && sessionId != null) {
                memoryManager.addAsync(userId, Long.parseLong(CHAT_AGENT_ID), sessionId,
                        List.of(
                                Map.of("role", "user", "content", body.getOrDefault("message", "").toString()),
                                Map.of("role", "assistant", "content", body.getOrDefault("response", "").toString())
                        ));
            }

            return Response.<String>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data("ok")
                    .build();
        } catch (Exception e) {
            log.error("记忆抽取触发失败", e);
            return Response.<String>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /** 用长期记忆丰富消息 */
    private String enrichWithMemory(Long userId, String message) {
        if (userId == null) return message;
        try {
            String memoryContext = memoryManager.retrieveContext(userId, message, 5);
            if (memoryContext == null || memoryContext.isBlank()) return message;
            return "【用户记忆上下文】\n" + memoryContext + "\n\n【当前消息】\n" + message;
        } catch (Exception e) {
            log.warn("记忆注入失败 userId={}: {}", userId, e.getMessage());
            return message;
        }
    }
}
