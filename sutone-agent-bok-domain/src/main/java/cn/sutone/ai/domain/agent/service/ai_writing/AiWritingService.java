package cn.sutone.ai.domain.agent.service.ai_writing;

import cn.sutone.ai.domain.agent.adapter.repository.IAiTaskRepository;
import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingStreamEventVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingTaskTypeVO;
import cn.sutone.ai.domain.agent.service.IAiWritingService;
import cn.sutone.ai.domain.agent.service.IChatService;
import cn.sutone.ai.domain.agent.service.ai_writing.markdown.MarkdownBlockRenderer;
import cn.sutone.ai.domain.agent.service.ai_writing.markdown.MarkdownNormalizer;
import cn.sutone.ai.domain.agent.service.ratelimit.RateLimitService;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.service.draft.DraftDomainService;
import cn.sutone.ai.types.common.RedisKeyConstants;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import com.google.adk.events.Event;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * AI 写作服务实现
 */
@Slf4j
@Service
public class AiWritingService implements IAiWritingService {

    private static final String WRITING_AGENT_ID = "300002";
    private static final String DRAWIO_AGENT_ID = "300000";
    private static final String ILLUSTRATION_AGENT_ID = "300003";

    // 与 agent-writing.yml 中 agents[].name 严格对齐
    private static final String AUTHOR_ANALYST = "agent_writing_analyst";
    private static final String AUTHOR_GENERATOR = "agent_writing_generator";
    private static final String AUTHOR_REVIEWER = "agent_writing_reviewer";

    private static final Map<String, String> AUTHOR_PHASE_MAP = Map.of(
            AUTHOR_ANALYST, "analyzing",
            AUTHOR_GENERATOR, "generating",
            AUTHOR_REVIEWER, "reviewing"
    );

    private static final Map<String, String> PHASE_LABEL_MAP = Map.of(
            "analyzing", "正在分析草稿上下文...",
            "generating", "正在生成写作内容...",
            "illustrating", "正在识别配图需求...",
            "reviewing", "正在进行质量审查..."
    );

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IChatService chatService;
    private final IAiTaskRepository aiTaskRepository;
    private final DraftDomainService draftDomainService;
    private final RateLimitService rateLimitService;
    private final RedissonClient redissonClient;

    public AiWritingService(IChatService chatService, IAiTaskRepository aiTaskRepository,
                            DraftDomainService draftDomainService, RateLimitService rateLimitService,
                            RedissonClient redissonClient) {
        this.chatService = chatService;
        this.aiTaskRepository = aiTaskRepository;
        this.draftDomainService = draftDomainService;
        this.rateLimitService = rateLimitService;
        this.redissonClient = redissonClient;
    }

    @Override
    public AiTaskEntity submitTask(Long userId, Long draftId, String taskTypeCode, Map<String, Object> promptParams, Boolean enableIllustration) {
        if (!rateLimitService.tryAcquire(userId)) {
            throw new AppException(ResponseCode.E0001.getCode(), "AI 请求过于频繁，请稍后再试");
        }

        String lockKey = RedisKeyConstants.AI_TASK_LOCK_PREFIX + userId + ":" + draftId + ":" + taskTypeCode;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(0, 5, TimeUnit.SECONDS)) {
                throw new AppException(ResponseCode.E0001.getCode(), "请勿重复提交，上个任务仍在处理中");
            }
            DraftEntity draft = draftDomainService.queryDraftDetail(draftId, userId);
            draft.checkEditable();
            AiWritingTaskTypeVO taskType = AiWritingTaskTypeVO.fromCode(taskTypeCode);
            String prompt = buildPrompt(draft, taskType, promptParams);

            Long taskId = aiTaskRepository.nextTaskId();
            AiTaskEntity task = AiTaskEntity.initPending(taskId, userId, draftId, taskType, prompt, enableIllustration);
            aiTaskRepository.save(task);
            return task;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ResponseCode.E0001.getCode(), "系统繁忙，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public AiTaskEntity queryTask(Long taskId, Long userId) {
        AiTaskEntity task = aiTaskRepository.queryById(taskId);
        if (null == task) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "AI 任务不存在");
        }
        task.validateOwner(userId);
        return task;
    }

    @Override
    public void generateStream(Long taskId, Long userId, Consumer<AiWritingStreamEventVO> eventConsumer) {
        AiTaskEntity task = queryTask(taskId, userId);

        task.startRunning();
        aiTaskRepository.update(task);

        String agentId = resolveAgentId();
        String sessionId = chatService.createSession(agentId, String.valueOf(userId));
        StringBuilder responseBuilder = new StringBuilder();
        StringBuilder reviewerLineBuffer = new StringBuilder();

        boolean enableIllustration = Boolean.TRUE.equals(task.getEnableIllustration());
        log.info("generateStream taskId={} enableIllustration={}", taskId, enableIllustration);

        try {
            Flowable<Event> events = chatService.handleMessageStream(agentId, String.valueOf(userId), sessionId, task.getPromptPayload());
            String[] currentPhase = {null};
            events.blockingForEach(event -> {
                if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) {
                    return;
                }
                String author = event.author();
                String newPhase = AUTHOR_PHASE_MAP.getOrDefault(author, "thinking");
                if (!Objects.equals(newPhase, currentPhase[0])) {
                    currentPhase[0] = newPhase;
                    String label = PHASE_LABEL_MAP.getOrDefault(newPhase, "思考中...");
                    eventConsumer.accept(statusEvent(newPhase, label));
                }
                String content = event.stringifyContent();
                if (null == content || content.isBlank()) {
                    return;
                }
                if (AUTHOR_ANALYST.equals(author)) {
                    return;
                }
                if (AUTHOR_GENERATOR.equals(author)) {
                    eventConsumer.accept(tokenEvent(newPhase, content));
                    return;
                }
                // reviewer：终稿
                boolean isPartial = event.partial().orElse(false);
                reviewerLineBuffer.append(content);
                if (isPartial && reviewerLineBuffer.indexOf("\n") < 0) {
                    return;
                }
                String accumulated = reviewerLineBuffer.toString();
                String[] lines = accumulated.split("\n", -1);
                int processUpTo = isPartial ? lines.length - 1 : lines.length;
                reviewerLineBuffer.setLength(0);
                if (isPartial && lines.length > 0 && !lines[lines.length - 1].isEmpty()) {
                    reviewerLineBuffer.append(lines[lines.length - 1]);
                }
                for (int i = 0; i < processUpTo; i++) {
                    consumeReviewerLine(newPhase, lines[i], responseBuilder, eventConsumer);
                }
            });
            // flush reviewer 残留缓冲
            if (reviewerLineBuffer.length() > 0) {
                consumeReviewerLine("reviewing", reviewerLineBuffer.toString(), responseBuilder, eventConsumer);
            }

            // 配图分析 + 子会话编排（仅 enableIllustration=true 时执行）
            List<IllustrationRequest> illustrationRequests = enableIllustration
                    ? analyzeIllustrations(userId, responseBuilder.toString())
                    : List.of();
            log.info("illustration processing: enableIllustration={} requestCount={}", enableIllustration, illustrationRequests.size());
            if (!illustrationRequests.isEmpty()) {
                eventConsumer.accept(statusEvent("illustrating", "正在生成配图..."));
                for (IllustrationRequest req : illustrationRequests) {
                    try {
                        String drawXml = generateIllustration(userId, req);
                        if (null != drawXml && !drawXml.isBlank()) {
                            injectIllustration(responseBuilder, req.anchor(), drawXml, eventConsumer);
                        }
                    } catch (Exception e) {
                        log.error("生成配图失败 anchor={}: {}", req.anchor(), e.getMessage());
                    }
                }
            }

            String rawContent = responseBuilder.toString();
            log.info("=== [DIAG] formatMarkdown 前 (responseBuilder 前2000字) ===\n{}",
                    rawContent.length() <= 2000 ? rawContent : rawContent.substring(0, 2000) + "...[truncated]");
            String formattedContent = formatMarkdown(rawContent);
            log.info("=== [DIAG] formatMarkdown 后 (前2000字) ===\n{}",
                    formattedContent.length() <= 2000 ? formattedContent : formattedContent.substring(0, 2000) + "...[truncated]");
            markSuccess(task, formattedContent);
            eventConsumer.accept(resultEvent(formattedContent));
            eventConsumer.accept(doneEvent());
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (null == errorMsg || errorMsg.isBlank()) {
                errorMsg = e.getClass().getSimpleName();
            }
            markFailed(task, errorMsg);
            eventConsumer.accept(errorEvent(errorMsg));
        }
    }

    private record IllustrationRequest(String anchor, String diagramType, String requirement) {}

    private List<IllustrationRequest> analyzeIllustrations(Long userId, String articleContent) {
        String prompt = buildIllustrationPrompt(articleContent);
        String sessionId = chatService.createSession(ILLUSTRATION_AGENT_ID, String.valueOf(userId));
        List<String> outputs = chatService.handleMessage(
                ILLUSTRATION_AGENT_ID, String.valueOf(userId), sessionId, prompt);

        List<IllustrationRequest> requests = new ArrayList<>();
        for (String line : outputs) {
            if (null == line || line.isBlank()) {
                continue;
            }
            try {
                JsonNode json = objectMapper.readTree(line.trim());
                if (json.has("none") && json.get("none").asBoolean()) {
                    requests.clear();
                    break;
                }
                String anchor = json.has("anchor") ? json.get("anchor").asText() : null;
                String diagramType = json.has("diagramType") ? json.get("diagramType").asText() : null;
                String requirement = json.has("requirement") ? json.get("requirement").asText() : null;
                if (null != anchor && null != diagramType && null != requirement) {
                    log.info("配图分析识别到需求 anchor={} type={}", anchor, diagramType);
                    requests.add(new IllustrationRequest(anchor, diagramType, requirement));
                }
            } catch (Exception e) {
                log.warn("解析配图分析结果失败，跳过该行: {}", line, e);
            }
        }
        return requests;
    }

    private String buildIllustrationPrompt(String articleContent) {
        return """
                分析以下技术文章，判断哪些段落适合配图。你必须且只能输出 JSON，每行一条，格式如下：
                {"type":"illustration_request","anchor":"段落标识","diagramType":"architecture|flowchart|sequence","requirement":"具体画什么"}
                规则：系统架构→architecture，业务流程→flowchart，调用时序→sequence。最多3条。
                若无需配图，输出：{"type":"illustration_request","none":true}
                严禁输出 JSON 以外的任何内容。

                ---文章内容---
                %s
                """.formatted(articleContent);
    }

    private String generateIllustration(Long userId, IllustrationRequest req) {
        String drawSessionId = chatService.createSession(DRAWIO_AGENT_ID, String.valueOf(userId));
        String drawPrompt = """
                请根据以下绘图需求，生成一个 draw.io 图表。
                图表类型：%s
                需求描述：%s
                """.formatted(req.diagramType(), req.requirement());

        Flowable<Event> drawEvents = chatService.handleMessageStream(
                DRAWIO_AGENT_ID, String.valueOf(userId), drawSessionId, drawPrompt);

        String[] drawXml = {null};
        // 按 author 缓冲文本，避免跨 Agent 的 JSON 误解析
        java.util.Map<String, StringBuilder> authorBuffers = new java.util.LinkedHashMap<>();
        drawEvents.blockingForEach(event -> {
            if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) {
                return;
            }
            String author = event.author();
            String content = event.stringifyContent();
            if (null == content || content.isBlank() || null == author) {
                return;
            }
            // 只处理 agent_drawer 的输出（它是真正产生 JSON 的 agent）
            if (!"agent_drawer".equals(author)) {
                return;
            }

            boolean isPartial = event.partial().orElse(false);
            StringBuilder buffer = authorBuffers.computeIfAbsent(author, k -> new StringBuilder());
            buffer.append(content);

            String accumulated = buffer.toString();
            if (isPartial && accumulated.indexOf('\n') < 0) {
                return;
            }

            String[] lines = accumulated.split("\n", -1);
            String remaining = lines[lines.length - 1];
            buffer.setLength(0);
            if (!remaining.isEmpty()) {
                buffer.append(remaining);
            }

            int processUpTo = isPartial ? lines.length - 1 : lines.length;
            for (int i = 0; i < processUpTo; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    JsonNode json = objectMapper.readTree(line);
                    String type = json.has("type") ? json.get("type").asText() : null;
                    if ("drawio_done".equals(type)) {
                        drawXml[0] = json.has("content") ? json.get("content").asText() : null;
                        log.info("generateIllustration 提取到 drawio_done XML length={}",
                                drawXml[0] != null ? drawXml[0].length() : 0);
                    }
                } catch (Exception e) {
                    // 跳过非 JSON 行（正常情况：XML 内容行等）
                }
            }
        });
        // flush 残留缓冲
        for (java.util.Map.Entry<String, StringBuilder> entry : authorBuffers.entrySet()) {
            String remaining = entry.getValue().toString().trim();
            if (!remaining.isEmpty()) {
                try {
                    JsonNode json = objectMapper.readTree(remaining);
                    String type = json.has("type") ? json.get("type").asText() : null;
                    if ("drawio_done".equals(type)) {
                        drawXml[0] = json.has("content") ? json.get("content").asText() : null;
                        log.info("generateIllustration flush 提取到 drawio_done XML length={}",
                                drawXml[0] != null ? drawXml[0].length() : 0);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        log.info("generateIllustration 完成 anchor={} xmlNull={}", req.anchor(), drawXml[0] == null);
        return drawXml[0];
    }

    private void injectIllustration(StringBuilder responseBuilder, String anchor,
                                     String drawXml, Consumer<AiWritingStreamEventVO> eventConsumer) {
        String diagramBlock = "\n```drawio\n" + drawXml + "\n```\n";
        int anchorPos = findAnchor(responseBuilder, anchor);
        log.info("injectIllustration anchor='{}' found={}", anchor, anchorPos >= 0);
        if (anchorPos >= 0) {
            int insertPos = anchorPos + anchor.length();
            int lineEnd = responseBuilder.indexOf("\n", insertPos);
            if (lineEnd >= 0) {
                responseBuilder.insert(lineEnd, "\n" + diagramBlock);
            } else {
                responseBuilder.insert(insertPos, "\n" + diagramBlock);
            }
        } else {
            responseBuilder.append("\n").append(diagramBlock);
        }
        eventConsumer.accept(tokenEvent("illustrating", diagramBlock));
    }

    /**
     * 在正文中查找 anchor，支持降级匹配：精确 → 去首尾空白 → 最长的词
     */
    private int findAnchor(StringBuilder text, String anchor) {
        if (null == anchor || anchor.isBlank()) {
            return -1;
        }
        // 1. 精确匹配
        int pos = text.indexOf(anchor);
        if (pos >= 0) return pos;
        // 2. 去首尾空白后匹配
        String trimmed = anchor.trim();
        if (!trimmed.equals(anchor)) {
            pos = text.indexOf(trimmed);
            if (pos >= 0) return pos;
        }
        // 3. 取 anchor 中最长的词（大概率是核心关键词）
        String[] words = trimmed.split("\\s+");
        String longest = "";
        for (String w : words) {
            if (w.length() > longest.length()) longest = w;
        }
        if (longest.length() >= 3) {
            pos = text.indexOf(longest);
            if (pos >= 0) return pos;
        }
        return -1;
    }

    /**
     * 消费 reviewer 的一行输出：结构化块走确定性渲染，自由文本原样累积。
     * 渲染结果既累积进最终落库内容，也作为 token 推给前端预览。
     */
    private void consumeReviewerLine(String phase, String line, StringBuilder responseBuilder,
                                     Consumer<AiWritingStreamEventVO> eventConsumer) {
        if (null == line) {
            return;
        }
        // 空行需要作为段落分隔符同时发给前端和落库，否则流式渲染时缺少段落边界
        if (line.isBlank()) {
            responseBuilder.append("\n");
            eventConsumer.accept(tokenEvent(phase, "\n"));
            return;
        }
        if (MarkdownBlockRenderer.isBlockLine(line)) {
            String fragment = MarkdownBlockRenderer.renderLine(line);
            if (null == fragment || fragment.isEmpty()) {
                return;
            }
            responseBuilder.append(fragment).append("\n\n");
            eventConsumer.accept(tokenEvent(phase, fragment + "\n\n", line.trim()));
        } else {
            responseBuilder.append(line).append("\n");
            eventConsumer.accept(tokenEvent(phase, line + "\n"));
        }
    }

    // ==================== public ====================

    @Override
    public List<AiTaskEntity> queryTaskList(Long draftId, Long userId, int limit) {
        // 验证草稿归属
        draftDomainService.queryDraftDetail(draftId, userId);
        return aiTaskRepository.queryLatestByDraftId(draftId, limit);
    }

    // ==================== private ====================

    private String resolveAgentId() {
        List<AiAgentConfigTableVO.Agent> agents = chatService.queryAiAgentConfigList();
        if (null == agents || agents.isEmpty()) {
            throw new AppException(ResponseCode.E0001.getCode(), "没有可用的 Agent 配置");
        }
        return agents.stream()
                .filter(agent -> WRITING_AGENT_ID.equals(agent.getAgentId()))
                .findFirst()
                .map(AiAgentConfigTableVO.Agent::getAgentId)
                .orElseThrow(() -> new AppException(ResponseCode.E0001.getCode(), "未找到 AI 技术写作智能体配置"));
    }

    private void markSuccess(AiTaskEntity task, String responseContent) {
        task.markSuccess(responseContent);
        aiTaskRepository.update(task);
    }

    private void markFailed(AiTaskEntity task, String errorMsg) {
        task.markFailed(errorMsg);
        aiTaskRepository.update(task);
    }

    private String buildPrompt(DraftEntity draft, AiWritingTaskTypeVO taskType, Map<String, Object> promptParams) {
        String extraParams = null == promptParams || promptParams.isEmpty() ? "{}" : String.valueOf(promptParams);
        String customInstruction = null == promptParams ? null : (String) promptParams.get("customInstruction");
        String selectedText = null == promptParams ? null : (String) promptParams.get("selectedText");
        String formatInstruction = null == promptParams ? null : (String) promptParams.get("formatInstruction");
        String customSuffix = null == customInstruction || customInstruction.isBlank() ? "" : "\n\n用户额外指令：%s".formatted(customInstruction);
        String formatHardRule = null == formatInstruction || formatInstruction.isBlank() ? "" : "\n\n【格式硬约束 - 必须遵守】\n%s".formatted(formatInstruction);
        return switch (taskType) {
            case GENERATE_OUTLINE -> """
                    你是一个高级技术写作 Agent。请基于当前草稿上下文，为这篇技术文章生成 Markdown 大纲。
                    要求：结构清晰、层级合理、适合技术社区文章，不要输出解释说明，只输出大纲。
                    %s

                    标题：%s
                    摘要：%s
                    当前正文：
                    %s

                    额外参数：%s%s
                    """.formatted(formatHardRule, nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), nullToEmpty(draft.getContentMd()), extraParams, customSuffix);
            case GENERATE_BODY -> """
                    你是一个高级技术写作 Agent。请基于当前草稿上下文续写正文，输出 Markdown 内容。
                    要求：保持技术准确、表达自然、结构连贯，不要重复已有正文，不要输出解释说明。
                    注意：不要输出文章标题（# xxx），标题已在草稿中，直接从 ## 或正文内容开始写。
                    %s

                    标题：%s
                    摘要：%s
                    当前正文：
                    %s

                    额外参数：%s%s
                    """.formatted(formatHardRule, nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), nullToEmpty(draft.getContentMd()), extraParams, customSuffix);
            case POLISH_TEXT -> {
                boolean hasSelectedText = null != selectedText && !selectedText.isBlank();
                String body = hasSelectedText ? selectedText : nullToEmpty(draft.getContentMd());
                String desc = hasSelectedText
                    ? "请对以下选中文本进行润色改写，只输出改写结果，不要输出解释说明。不要输出文章标题（# xxx）。"
                    : "请对当前草稿进行智能处理：\n- 如果内容是**大纲/提纲**（标题多、正文少），请将每个章节展开为完整正文段落，保留原目录结构；\n- 如果已是完整正文，则优化表达质量和阅读流畅度。\n要求：不要输出解释说明。不要输出文章标题（# xxx），标题已在草稿中。";
                yield """
                    你是一个高级技术写作 Agent。%s
                    %s

                    标题：%s
                    摘要：%s
                    待处理文本：
                    %s

                    额外参数：%s%s
                    """.formatted(desc, formatHardRule, nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), body, extraParams, customSuffix);
            }
            case SUMMARIZE -> """
                    你是一个高级技术写作 Agent。请基于当前草稿生成一段适合发布页展示的文章摘要。
                    要求：100 到 200 字，突出主题、技术价值和读者收益，不要输出解释说明。

                    标题：%s
                    当前正文：
                    %s

                    额外参数：%s%s
                    """.formatted(nullToEmpty(draft.getTitle()), nullToEmpty(draft.getContentMd()), extraParams, customSuffix);
            case GENERATE_TITLE -> """
                    你是一个高级技术写作 Agent。请基于当前草稿生成 3 到 5 个候选标题。
                    要求：吸引技术读者、突出文章核心价值、简洁有力，每个标题一行，不要输出解释说明。

                    标题：%s
                    摘要：%s
                    当前正文：
                    %s

                    额外参数：%s%s
                    """.formatted(nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), nullToEmpty(draft.getContentMd()), extraParams, customSuffix);
            case GENERATE_TAGS -> """
                    你是一个高级技术写作 Agent。请分析当前草稿内容，生成 3 到 5 个相关技术标签。
                    要求：标签应覆盖主要技术栈和主题，用英文逗号分隔，不要输出解释说明。

                    标题：%s
                    摘要：%s
                    当前正文：
                    %s

                    额外参数：%s%s
                    """.formatted(nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), nullToEmpty(draft.getContentMd()), extraParams, customSuffix);
            case QUALITY_CHECK -> """
                    你是一个高级技术写作 Agent。请对当前草稿进行发布质量检查。
                    检查项：拼写错误、语法问题、结构完整性、代码正确性、技术准确性。
                    要求：逐项列出问题及改进建议，如无问题则输出"质量检查通过"，不要输出多余解释。

                    标题：%s
                    摘要：%s
                    当前正文：
                    %s

                    额外参数：%s%s
                    """.formatted(nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), nullToEmpty(draft.getContentMd()), extraParams, customSuffix);
        };
    }

    private String nullToEmpty(String value) {
        return null == value ? "" : value;
    }

    private AiWritingStreamEventVO statusEvent(String phase, String content) {
        return buildEvent(phase, "status", content);
    }

    private AiWritingStreamEventVO tokenEvent(String phase, String content) {
        return buildEvent(phase, "token", content);
    }

    private AiWritingStreamEventVO tokenEvent(String phase, String content, String raw) {
        return AiWritingStreamEventVO.builder()
                .phase(phase)
                .chunk(AiWritingStreamEventVO.Chunk.builder()
                        .type("token")
                        .content(content)
                        .raw(raw)
                        .build())
                .build();
    }

    private AiWritingStreamEventVO doneEvent() {
        return buildEvent("done", "done", "");
    }

    private AiWritingStreamEventVO resultEvent(String content) {
        return buildEvent("done", "result", content);
    }

    private AiWritingStreamEventVO errorEvent(String content) {
        return buildEvent("error", "error", content);
    }

    /**
     * 规范化 AI 输出的 Markdown。
     *
     * <p>层二方案：委托 {@link MarkdownNormalizer} 用 CommonMark 解析成 AST 再重新序列化，
     * 从根本上避免正则「误切合法标题」与「漏切无穷畸形」的两难。少量结构预处理与行内 LaTeX
     * 转义修复已收敛进 {@code MarkdownNormalizer.preprocess}，此处不再堆叠正则。</p>
     */
    private String formatMarkdown(String raw) {
        return MarkdownNormalizer.normalize(raw);
    }

    private AiWritingStreamEventVO buildEvent(String phase, String type, String content) {
        return AiWritingStreamEventVO.builder()
                .phase(phase)
                .chunk(AiWritingStreamEventVO.Chunk.builder()
                        .type(type)
                        .content(content)
                        .build())
                .build();
    }
}
