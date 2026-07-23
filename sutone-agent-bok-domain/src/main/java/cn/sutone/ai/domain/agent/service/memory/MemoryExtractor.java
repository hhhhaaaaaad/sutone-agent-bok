package cn.sutone.ai.domain.agent.service.memory;

import cn.sutone.ai.domain.agent.adapter.repository.IMemoryEmbeddingClient;
import cn.sutone.ai.domain.agent.adapter.repository.IMemoryVectorStore;
import cn.sutone.ai.domain.agent.model.entity.MemoryRecordEntity;
import cn.sutone.ai.domain.agent.model.valobj.MemoryCandidate;
import cn.sutone.ai.domain.agent.model.valobj.MemoryTypeVO;
import cn.sutone.ai.domain.agent.model.valobj.ScoredMemory;
import cn.sutone.ai.domain.agent.model.valobj.properties.MemoryProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 记忆抽取器
 * 负责调用 LLM 从对话中提取结构化记忆，并判断是否需要 UPDATE 已有记忆
 */
@Slf4j
@Component
public class MemoryExtractor {

    @Resource
    private IMemoryEmbeddingClient embeddingClient;

    @Resource
    private IMemoryVectorStore vectorStore;

    @Resource
    private cn.sutone.ai.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private MemoryProperties memoryProperties;

    private String promptTemplate;
    private OpenAiApi chatOpenAiApi;
    private String chatModel;

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final int MAX_LLM_RETRIES = 3;
    private static final long LLM_RETRY_BACKOFF_MS = 2000;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/memory-extraction.txt");
            promptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("记忆抽取 prompt 模板加载完成");
        } catch (IOException e) {
            log.error("加载记忆抽取 prompt 模板失败", e);
            promptTemplate = "";
        }

        // 构建独立的 chat OpenAiApi（用 DeepSeek 配置，不用 embedding 的硅基流动）
        try {
            var writingConfig = aiAgentAutoConfigProperties.getTables().get("writingAgent");
            var apiConfig = writingConfig.getModule().getAiApi();
            this.chatOpenAiApi = OpenAiApi.builder()
                    .baseUrl(apiConfig.getBaseUrl())
                    .apiKey(apiConfig.getApiKey())
                    .completionsPath(org.apache.commons.lang3.StringUtils.isNotBlank(apiConfig.getCompletionsPath())
                            ? apiConfig.getCompletionsPath() : "v1/chat/completions")
                    .build();
            this.chatModel = writingConfig.getModule().getChatModel().getModel();
            log.info("MemoryExtractor LLM 初始化完成, model={}", chatModel);
        } catch (Exception e) {
            log.error("MemoryExtractor LLM 初始化失败", e);
        }
    }

    /**
     * 从对话中抽取候选记忆
     *
     * @param existingMemories 已有记忆（传入 LLM 做第一层去重）
     * @param newMessages      新对话消息
     * @param lastMessages     最近上下文（帮助理解代词指代）
     * @return 候选记忆列表
     */
    public List<MemoryCandidate> extract(List<MemoryRecordEntity> existingMemories,
                                         List<Map<String, String>> newMessages,
                                         List<String> lastMessages) {
        // 构建 prompt
        String existingMemoriesStr = existingMemories.stream()
                .map(m -> "{\"id\":\"%d\",\"text\":\"%s\"}".formatted(m.getId(), m.getContent()))
                .collect(Collectors.joining(", ", "[", "]"));

        String lastMessagesStr = lastMessages != null && !lastMessages.isEmpty()
                ? String.join("\n", lastMessages)
                : "（无）";

        String newMessagesStr = newMessages.stream()
                .map(m -> "[%s]: %s".formatted(m.get("role"), m.get("content")))
                .collect(Collectors.joining("\n"));

        String userPrompt = promptTemplate
                .replace("{existing_memories}", existingMemoriesStr)
                .replace("{last_messages}", lastMessagesStr)
                .replace("{new_messages}", newMessagesStr);

        // 调用 LLM
        String llmResponse = callLlm(userPrompt);
        if (llmResponse == null || llmResponse.isBlank()) {
            log.info("MemoryExtractor LLM 返回空");
            return Collections.emptyList();
        }
        log.info("MemoryExtractor LLM 原始响应 (前200字符): {}", llmResponse.substring(0, Math.min(200, llmResponse.length())));

        // 防御性 JSON 解析
        return parseResponse(llmResponse);
    }

    /**
     * 判断候选记忆是否应该 UPDATE 而非 ADD（cosine > 0.9）
     * V2: 使用向量搜索 top-1 替代逐条 getVector，Qdrant 下避免 N 次 HTTP 请求
     *
     * @return 应该被更新的已有记忆 ID，null 表示应该新增
     */
    public Long findUpdateTarget(float[] candidateEmbedding, Long userId) {
        List<ScoredMemory> results = vectorStore.search(userId, candidateEmbedding, 1);
        if (results.isEmpty()) return null;
        if (results.get(0).score() > 0.9) {
            return results.get(0).id();
        }
        return null;
    }

    /** 调用 LLM chat completions（带重试） */
    private String callLlm(String userPrompt) {
        if (chatOpenAiApi == null) {
            log.warn("MemoryExtractor chatOpenAiApi 未初始化");
            return null;
        }
        for (int attempt = 0; attempt < MAX_LLM_RETRIES; attempt++) {
            try {
                var messages = List.of(
                        new OpenAiApi.ChatCompletionMessage(userPrompt, OpenAiApi.ChatCompletionMessage.Role.USER)
                );
                var request = new OpenAiApi.ChatCompletionRequest(
                        messages, chatModel, 0.3, false
                );
                ResponseEntity<OpenAiApi.ChatCompletion> response = chatOpenAiApi.chatCompletionEntity(request);
                if (response != null && response.getBody() != null
                        && response.getBody().choices() != null && !response.getBody().choices().isEmpty()) {
                    return response.getBody().choices().get(0).message().content();
                }
                return null;
            } catch (Exception e) {
                if (attempt < MAX_LLM_RETRIES - 1) {
                    log.warn("记忆抽取 LLM 重试 {}/{}: {}", attempt + 1, MAX_LLM_RETRIES, e.getMessage());
                    try { Thread.sleep(LLM_RETRY_BACKOFF_MS); } catch (InterruptedException ignored) {}
                } else {
                    log.error("记忆抽取 LLM 调用失败，已达最大重试次数: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    /** 防御性 JSON 解析：处理 code fence、多余文字等 */
    private List<MemoryCandidate> parseResponse(String llmResponse) {
        try {
            // 去除 markdown code fence
            String cleaned = llmResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");

            // 正则提取最外层 JSON 对象
            Matcher matcher = JSON_PATTERN.matcher(cleaned);
            if (!matcher.find()) return Collections.emptyList();
            String jsonStr = matcher.group();

            // 解析
            JSONObject obj = JSON.parseObject(jsonStr);
            JSONArray memories = obj.getJSONArray("memory");
            if (memories == null || memories.isEmpty()) return Collections.emptyList();

            // 逐条验证
            List<MemoryCandidate> result = new ArrayList<>();
            for (int i = 0; i < memories.size(); i++) {
                JSONObject m = memories.getJSONObject(i);
                String text = m.getString("text");
                String type = m.getString("type");
                String attributedTo = m.getString("attributed_to");

                // 校验
                int maxLen = memoryProperties != null ? memoryProperties.getExtraction().getMaxContentLength() : 500;
                if (text == null || text.isBlank() || text.length() > maxLen) continue;
                if (!MemoryTypeVO.isValid(type)) continue;

                result.add(new MemoryCandidate(text.trim(), type, attributedTo));
            }
            return result;
        } catch (Exception e) {
            log.warn("记忆抽取 JSON 解析失败, 原始响应: {}", llmResponse.substring(0, Math.min(200, llmResponse.length())), e);
            return Collections.emptyList();
        }
    }

    /** 余弦相似度 */
    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }
}
