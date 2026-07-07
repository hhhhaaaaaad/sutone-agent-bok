package cn.sutone.ai.domain.agent.service.ai_writing;

import cn.sutone.ai.domain.agent.adapter.repository.IAiTaskRepository;
import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingStreamEventVO;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingTaskTypeVO;
import cn.sutone.ai.domain.agent.service.IAiWritingService;
import cn.sutone.ai.domain.agent.service.IChatService;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.service.draft.DraftDomainService;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 写作服务实现
 */
@Service
public class AiWritingService implements IAiWritingService {

    private static final String WRITING_AGENT_ID = "300002";

    private final IChatService chatService;
    private final IAiTaskRepository aiTaskRepository;
    private final DraftDomainService draftDomainService;

    public AiWritingService(IChatService chatService, IAiTaskRepository aiTaskRepository, DraftDomainService draftDomainService) {
        this.chatService = chatService;
        this.aiTaskRepository = aiTaskRepository;
        this.draftDomainService = draftDomainService;
    }

    @Override
    public AiTaskEntity submitTask(Long userId, Long draftId, String taskTypeCode, Map<String, Object> promptParams) {
        DraftEntity draft = draftDomainService.queryDraftDetail(draftId, userId);
        draft.checkEditable();
        AiWritingTaskTypeVO taskType = AiWritingTaskTypeVO.fromCode(taskTypeCode);
        String prompt = buildPrompt(draft, taskType, promptParams);

        Long taskId = aiTaskRepository.nextTaskId();
        AiTaskEntity task = AiTaskEntity.initPending(taskId, userId, draftId, taskType, prompt);
        aiTaskRepository.save(task);
        return task;
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

        // 标记任务为运行中
        task.startRunning();
        aiTaskRepository.update(task);

        String agentId = resolveAgentId();
        String sessionId = chatService.createSession(agentId, String.valueOf(userId));
        StringBuilder responseBuilder = new StringBuilder();

        eventConsumer.accept(statusEvent("正在分析草稿上下文..."));
        eventConsumer.accept(statusEvent("正在调用 Agent 写作能力..."));

        try {
            Flowable<Event> events = chatService.handleMessageStream(agentId, String.valueOf(userId), sessionId, task.getPromptPayload());
            events.blockingForEach(event -> {
                if (!event.functionCalls().isEmpty() || !event.functionResponses().isEmpty()) {
                    return;
                }
                String content = event.stringifyContent();
                if (null == content || content.isBlank()) {
                    return;
                }
                responseBuilder.append(content);
                eventConsumer.accept(tokenEvent(content));
            });
            markSuccess(task, responseBuilder.toString());
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
        String customSuffix = null == customInstruction || customInstruction.isBlank() ? "" : "\n\n用户额外指令：%s".formatted(customInstruction);
        return switch (taskType) {
            case GENERATE_OUTLINE -> """
                    你是一个高级技术写作 Agent。请基于当前草稿上下文，为这篇技术文章生成 Markdown 大纲。
                    要求：结构清晰、层级合理、适合技术社区文章，不要输出解释说明，只输出大纲。

                    标题：%s
                    摘要：%s
                    当前正文：
                    %s

                    额外参数：%s%s
                    """.formatted(nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), nullToEmpty(draft.getContentMd()), extraParams, customSuffix);
            case GENERATE_BODY -> """
                    你是一个高级技术写作 Agent。请基于当前草稿上下文续写正文，输出 Markdown 内容。
                    要求：保持技术准确、表达自然、结构连贯，不要重复已有正文，不要输出解释说明。

                    标题：%s
                    摘要：%s
                    当前正文：
                    %s

                    额外参数：%s%s
                    """.formatted(nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), nullToEmpty(draft.getContentMd()), extraParams, customSuffix);
            case POLISH_TEXT -> {
                boolean hasSelectedText = null != selectedText && !selectedText.isBlank();
                String body = hasSelectedText ? selectedText : nullToEmpty(draft.getContentMd());
                String desc = hasSelectedText ? "请对以下选中文本进行润色改写，只输出改写结果，不要输出解释说明。"
                        : "请对当前草稿进行润色改写，提升表达质量、技术严谨性和阅读流畅度。要求：保留原意，输出完整 Markdown 改写结果，不要输出解释说明。";
                yield """
                    你是一个高级技术写作 Agent。%s

                    标题：%s
                    摘要：%s
                    待处理文本：
                    %s

                    额外参数：%s%s
                    """.formatted(desc, nullToEmpty(draft.getTitle()), nullToEmpty(draft.getSummary()), body, extraParams, customSuffix);
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

    private AiWritingStreamEventVO statusEvent(String content) {
        return buildEvent("thinking", "status", content);
    }

    private AiWritingStreamEventVO tokenEvent(String content) {
        return buildEvent("generating", "token", content);
    }

    private AiWritingStreamEventVO doneEvent() {
        return buildEvent("done", "done", "");
    }

    private AiWritingStreamEventVO errorEvent(String content) {
        return buildEvent("error", "error", content);
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
