package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.IAgentService;
import cn.sutone.ai.api.dto.*;
import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.sutone.ai.domain.agent.service.IChatService;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import cn.sutone.ai.domain.agent.service.chat.CustomApiConfigManager;
import org.apache.commons.lang3.StringUtils;

/**
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AgentServiceController implements IAgentService {

    @Resource
    private IChatService chatService;

    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            log.info("查询智能体配置列表");

            List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();

            List<AiAgentConfigResponseDTO> responseDTOS = agentConfigs.stream().map(agentConfig -> {
                AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
                responseDTO.setAgentId(agentConfig.getAgentId());
                responseDTO.setAgentName(agentConfig.getAgentName());
                responseDTO.setAgentDesc(agentConfig.getAgentDesc());
                return responseDTO;
            }).collect(Collectors.toList());

            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOS)
                    .build();

        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询智能体配置列表失败", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.POST)
    @Override
    public Response<CreateSessionResponseDTO> createSession(@RequestBody CreateSessionRequestDTO requestDTO) {
        try {
            log.info("创建会话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());

            CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
            responseDTO.setSessionId(sessionId);

            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("创建会话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.GET)
    public Response<CreateSessionResponseDTO> createSession(@RequestParam("agentId") String agentId, @RequestParam("userId") String userId) {
        CreateSessionRequestDTO requestDTO = new CreateSessionRequestDTO();
        requestDTO.setAgentId(agentId);
        requestDTO.setUserId(userId);
        return createSession(requestDTO);
    }

    @RequestMapping(value = "chat", method = RequestMethod.POST)
    @Override
    public Response<ChatResponseDTO> chat(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("智能体对话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            // 保存用户自定义配置
            CustomApiConfigManager.CustomApiConfig config = CustomApiConfigManager.CustomApiConfig.builder()
                    .baseUrl(requestDTO.getCustomBaseUrl())
                    .apiKey(requestDTO.getCustomApiKey())
                    .completionsPath(requestDTO.getCustomCompletionsPath())
                    .model(requestDTO.getCustomModel())
                    .customModelSelected(StringUtils.isNotBlank(requestDTO.getCustomModel()))
                    .build();
            CustomApiConfigManager.setConfig(sessionId, config);

            List<String> messages = chatService.handleMessage(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage());

            ChatResponseDTO responseDTO = new ChatResponseDTO();
            try {
                // 尝试获取最后一条消息并解析
                String result = messages.stream().reduce((first, second) -> second).orElse("");
                ChatResponseDTO parsed = JSON.parseObject(result, ChatResponseDTO.class);
                if (null != parsed) {
                    responseDTO = parsed;
                    // 如果解析后的对象 type 为空，则默认为 user
                    if (null == responseDTO.getType()) {
                        responseDTO.setType("user");
                    }
                } else {
                    responseDTO.setType("user");
                    responseDTO.setContent(String.join("\n", messages));
                }
            } catch (Exception e) {
                responseDTO.setType("user");
                responseDTO.setContent(String.join("\n", messages));
            }

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("智能体对话异常", e);
            return Response.<ChatResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("智能体对话败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "chat_stream", method = RequestMethod.POST)
    @Override
    public ResponseBodyEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(5 * 60 * 1000L) {
            @Override
            protected void extendResponse(org.springframework.http.server.ServerHttpResponse outputMessage) {
                outputMessage.getHeaders().set("Content-Type", "application/x-ndjson");
            }
        };
        try {
            log.info("流式对话 agentId:{} userId:{} sessionId:{} message:{}", requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId(), requestDTO.getMessage());

            // Ensure session exists
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            final String finalSessionId = sessionId;

            // 保存用户自定义配置
            CustomApiConfigManager.CustomApiConfig config = CustomApiConfigManager.CustomApiConfig.builder()
                    .baseUrl(requestDTO.getCustomBaseUrl())
                    .apiKey(requestDTO.getCustomApiKey())
                    .completionsPath(requestDTO.getCustomCompletionsPath())
                    .model(requestDTO.getCustomModel())
                    .customModelSelected(StringUtils.isNotBlank(requestDTO.getCustomModel()))
                    .build();
            CustomApiConfigManager.setConfig(finalSessionId, config);

            // Accumulate partial text per author, detect complete JSON lines to flush incrementally
            final java.util.concurrent.ConcurrentHashMap<String, StringBuilder> authorBuffers = new java.util.concurrent.ConcurrentHashMap<>();

            io.reactivex.rxjava3.disposables.Disposable disposable = chatService.handleMessageStream(requestDTO.getAgentId(), requestDTO.getUserId(), finalSessionId, requestDTO.getMessage())
                    .subscribe(
                            event -> {
                                try {
                                    // Determine phase from author
                                    String author = event.author();
                                    String phase;
                                    switch (author != null ? author : "unknown") {
                                        case "agent_analyst":
                                        case "agent_ppt_analyst":
                                            phase = "analyzing";
                                            break;
                                        case "agent_drawer":
                                            phase = "drawing";
                                            break;
                                        case "agent_ppt_generator":
                                            phase = "generating";
                                            break;
                                        case "agent_reviewer":
                                        case "agent_ppt_reviewer":
                                            phase = "reviewing";
                                            break;
                                        default:
                                            phase = "thinking";
                                    }

                                    // Skip events that are purely function calls/responses (tool invocations)
                                    if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) {
                                        return;
                                    }

                                    String content = event.stringifyContent();
                                    if (content == null || content.isEmpty()) {
                                        return;
                                    }

                                    // For PPT generation and reviewing, stream the content directly as ppt_raw
                                    // This prevents buffering huge JSON strings and causing frontend timeouts
                                    if ("agent_ppt_generator".equals(author) || "agent_ppt_reviewer".equals(author)) {
                                        try {
                                            com.alibaba.fastjson.JSONObject wrapper = new com.alibaba.fastjson.JSONObject();
                                            wrapper.put("phase", phase);
                                            com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
                                            chunk.put("type", "ppt_raw");
                                            chunk.put("raw", content);
                                            wrapper.put("chunk", chunk);
                                            emitter.send(wrapper.toJSONString() + "\n");
                                        } catch (Exception ignored) {}
                                        return;
                                    }

                                    boolean isPartial = event.partial().orElse(false);

                                    StringBuilder buffer = authorBuffers.computeIfAbsent(author, k -> new StringBuilder());
                                    
                                    String currentActiveLine;
                                    int lastNewline = buffer.lastIndexOf("\n");
                                    if (lastNewline >= 0) {
                                        currentActiveLine = buffer.substring(lastNewline + 1);
                                    } else {
                                        currentActiveLine = buffer.toString();
                                    }
                                    currentActiveLine = currentActiveLine.trim();
                                    
                                    boolean isLikelyJson = currentActiveLine.startsWith("{") || (currentActiveLine.isEmpty() && content.trim().startsWith("{"));

                                    if (!isLikelyJson) {
                                        // 向前端发送实时的 token 数据，仅对非JSON数据发送，避免前端出现乱码
                                        try {
                                            com.alibaba.fastjson.JSONObject tokenMsg = new com.alibaba.fastjson.JSONObject();
                                            tokenMsg.put("phase", phase);
                                            com.alibaba.fastjson.JSONObject tokenChunk = new com.alibaba.fastjson.JSONObject();
                                            tokenChunk.put("type", "token");
                                            tokenChunk.put("content", content);
                                            tokenMsg.put("chunk", tokenChunk);
                                            emitter.send(tokenMsg.toJSONString() + "\n");
                                        } catch (Exception ignored) {}
                                    }

                                    buffer.append(content);

                                    // Check if the buffer contains complete JSON lines we can flush
                                    String accumulated = buffer.toString();

                                    // Process complete lines when:
                                    // 1. NOT a partial event (final event), OR
                                    // 2. Buffer contains a newline (at least one complete line)
                                    if (!isPartial || accumulated.contains("\n")) {
                                        String[] lines = accumulated.split("\n", -1);
                                        String remaining = lines[lines.length - 1];

                                        // Reset buffer
                                        buffer.setLength(0);
                                        if (!remaining.isEmpty()) {
                                            buffer.append(remaining);
                                        }

                                        // Process complete lines
                                        int processUpTo = isPartial ? lines.length - 1 : lines.length;
                                        for (int i = 0; i < processUpTo; i++) {
                                            String line = lines[i].trim();
                                            if (line.isEmpty()) continue;
                                            processAndSendLine(emitter, phase, line);
                                        }
                                    }

                                    // If this is a non-partial event (final), flush any remaining buffer
                                    if (!isPartial) {
                                        String remaining = buffer.toString().trim();
                                        buffer.setLength(0);
                                        if (!remaining.isEmpty()) {
                                            processAndSendLine(emitter, phase, remaining);
                                        }
                                    }
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            error -> {
                                if (error instanceof IllegalStateException && error.getMessage() != null && error.getMessage().contains("ResponseBodyEmitter has already completed")) {
                                    log.warn("流式对话已结束(客户端断开或主动完成)");
                                    return;
                                }
                                if (error instanceof java.io.IOException || (error.getMessage() != null && error.getMessage().contains("Broken pipe"))) {
                                    log.warn("流式对话连接断开: {}", error.getMessage());
                                    return;
                                }
                                // If the cause is one of the above
                                if (error.getCause() instanceof IllegalStateException && error.getCause().getMessage() != null && error.getCause().getMessage().contains("ResponseBodyEmitter has already completed")) {
                                    log.warn("流式对话已结束(客户端断开或主动完成)");
                                    return;
                                }
                                if (error.getCause() instanceof java.io.IOException) {
                                    log.warn("流式对话连接断开: {}", error.getCause().getMessage());
                                    return;
                                }
                                log.error("流式对话异常", error);
                                try {
                                    com.alibaba.fastjson.JSONObject errMsg = new com.alibaba.fastjson.JSONObject();
                                    errMsg.put("phase", "error");
                                    com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
                                    chunk.put("type", "error");
                                    chunk.put("content", "对话异常，请重试");
                                    errMsg.put("chunk", chunk);
                                    emitter.send(errMsg.toJSONString() + "\n");
                                } catch (Exception ignored) {}
                                emitter.completeWithError(error);
                            },
                            () -> {
                                // Flush any remaining buffers
                                for (StringBuilder buf : authorBuffers.values()) {
                                    String remaining = buf.toString().trim();
                                    if (!remaining.isEmpty()) {
                                        try {
                                            processAndSendLine(emitter, "done", remaining);
                                        } catch (Exception ignored) {}
                                    }
                                }
                                try {
                                    com.alibaba.fastjson.JSONObject doneMsg = new com.alibaba.fastjson.JSONObject();
                                    doneMsg.put("phase", "done");
                                    com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
                                    chunk.put("type", "done");
                                    doneMsg.put("chunk", chunk);
                                    emitter.send(doneMsg.toJSONString() + "\n");
                                } catch (Exception ignored) {}
                                emitter.complete();
                            }
                    );

            emitter.onCompletion(() -> {
                log.info("流式对话 emitter.onCompletion sessionId:{}", finalSessionId);
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            });
            emitter.onTimeout(() -> {
                log.info("流式对话 emitter.onTimeout sessionId:{}", finalSessionId);
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            });
            emitter.onError(e -> {
                log.info("流式对话 emitter.onError sessionId:{}", finalSessionId);
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            });
        } catch (Exception e) {
            log.error("流式对话失败", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * Process a single line: try to parse as drawio/PPT JSON, otherwise send as status text.
     */
    private void processAndSendLine(ResponseBodyEmitter emitter, String phase, String line) throws Exception {
        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(line);
            if (json != null && json.containsKey("type")) {
                String type = json.getString("type");
                if ("drawio_node".equals(type) || "drawio_edge".equals(type) || "drawio_done".equals(type) || "user".equals(type) || "drawio".equals(type)) {
                    // Structured drawio output - pass through with phase info
                    com.alibaba.fastjson.JSONObject wrapper = new com.alibaba.fastjson.JSONObject();
                    wrapper.put("phase", phase);
                    wrapper.put("chunk", json);
                    emitter.send(wrapper.toJSONString() + "\n");
                    
                    // 模拟人类画图操作停顿，让前端有足够的时间进行渲染
                    if ("drawio_node".equals(type) || "drawio_edge".equals(type)) {
                        Thread.sleep(250);
                    }
                    
                    return;
                }
                
                // PPT output: send entire JSON as ppt_raw for frontend incremental parsing
                if ("ppt".equals(type) || json.containsKey("slides")) {
                    com.alibaba.fastjson.JSONObject wrapper = new com.alibaba.fastjson.JSONObject();
                    wrapper.put("phase", phase);
                    com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
                    chunk.put("type", "ppt_raw");
                    chunk.put("raw", line);
                    wrapper.put("chunk", chunk);
                    emitter.send(wrapper.toJSONString() + "\n");
                    return;
                }
            }
        } catch (Exception parseEx) {
            // Not a JSON line, fall through to treat as text
        }

        // Check if this line looks like it might be part of a PPT JSON (contains slide-like structures)
        // Send as ppt_raw for incremental parsing on the frontend
        if (line.contains("\"slides\"") || line.contains("\"slideIndex\"") || line.contains("\"elements\"")) {
            com.alibaba.fastjson.JSONObject wrapper = new com.alibaba.fastjson.JSONObject();
            wrapper.put("phase", phase);
            com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
            chunk.put("type", "ppt_raw");
            chunk.put("raw", line);
            wrapper.put("chunk", chunk);
            emitter.send(wrapper.toJSONString() + "\n");
            return;
        }

        // Non-JSON content or unrecognized JSON - send as phase status
        com.alibaba.fastjson.JSONObject statusMsg = new com.alibaba.fastjson.JSONObject();
        com.alibaba.fastjson.JSONObject chunk = new com.alibaba.fastjson.JSONObject();
        chunk.put("type", "status");
        chunk.put("content", line);
        statusMsg.put("phase", phase);
        statusMsg.put("chunk", chunk);
        emitter.send(statusMsg.toJSONString() + "\n");
    }

}
