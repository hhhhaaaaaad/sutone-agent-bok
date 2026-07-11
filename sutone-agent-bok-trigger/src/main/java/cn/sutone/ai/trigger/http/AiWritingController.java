package cn.sutone.ai.trigger.http;
import cn.sutone.ai.api.dto.aiwriting.AiTaskDetailResponseDTO;
import cn.sutone.ai.api.dto.aiwriting.AiWritingChunkDTO;
import cn.sutone.ai.api.dto.aiwriting.AiWritingStreamEventDTO;
import cn.sutone.ai.api.dto.aiwriting.SubmitAiTaskRequestDTO;
import cn.sutone.ai.api.dto.aiwriting.SubmitAiTaskResponseDTO;
import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingStreamEventVO;
import cn.sutone.ai.domain.agent.service.IAiWritingService;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * AI 写作 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AiWritingController implements cn.sutone.ai.api.IAiWritingService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Long DEFAULT_USER_ID = 1L;

    @Resource
    private IAiWritingService aiWritingService;

    /**
     * 提交 AI 写作任务
     *
     * <p>使用场景：用户在编辑器中编辑草稿时，点击"AI 写作"按钮，选择写作类型（如续写、扩写、润色等），
     * 前端将草稿 ID、任务类型和提示参数提交至此接口，触发异步 AI 写作任务。</p>
     *
     * <p>调用链路：前端 POST → 当前接口 → {@link IAiWritingService#submitTask} → 创建 AiTask 记录并异步执行 → 返回 taskId</p>
     *
     * <p>前端在收到返回的 taskId 后，可通过 {@link #queryTaskDetail} 轮询任务状态，或通过 {@link #stream}
     * 建立 SSE 连接实时接收生成内容。</p>
     *
     * @param requestDTO 请求体，包含：
     *                   <ul>
     *                     <li>{@code draftId} - 草稿 ID，标识要编辑的文章草稿</li>
     *                     <li>{@code taskType} - 任务类型（如 continuation: 续写, expansion: 扩写, polish: 润色）</li>
     *                     <li>{@code promptParams} - 提示参数，额外控制生成风格、长度等</li>
     *                   </ul>
     * @return 提交结果，包含 taskId（后续查询/流式接口使用）、draftId、taskType、status 等
     */
    @PostMapping("ai-writing/task/submit")
    @Override
    public Response<SubmitAiTaskResponseDTO> submitTask(@RequestBody SubmitAiTaskRequestDTO requestDTO) {
        try {
            log.info("提交 AI 写作任务 draftId:{} taskType:{}", requestDTO.getDraftId(), requestDTO.getTaskType());
            AiTaskEntity task = aiWritingService.submitTask(
                    DEFAULT_USER_ID,
                    requestDTO.getDraftId(),
                    requestDTO.getTaskType(),
                    requestDTO.getPromptParams()
            );
            return Response.<SubmitAiTaskResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toSubmitDTO(task))
                    .build();
        } catch (Exception e) {
            log.error("提交 AI 写作任务失败", e);
            return fail(e);
        }
    }

    /**
     * 查询 AI 写作任务详情（轮询模式）
     *
     * <p>使用场景：前端在提交 AI 写作任务后，通过此接口轮询任务状态。当 AI 任务为同步/半同步模式时，
     * 前端每隔一段时间（如 2s）请求此接口，直到 status 变为 completed（成功）或 failed（失败），
     * 然后从 responseContent 中获取完整生成结果。</p>
     *
     * <p>与 {@link #stream} 的关系：stream 是 SSE 实时推送更适合长文本流式生成场景；
     * 此接口适用于不需要实时逐字展示、只需最终结果的场景（如润色、摘要生成）。</p>
     *
     * <p>如果前端使用 SSE 流式模式，则不需要调用此接口轮询，stream 完成后前端已经通过事件接收了全部内容。</p>
     *
     * @param taskId 任务 ID，由 {@link #submitTask} 返回
     * @return 任务详情，包括 status、statusDesc、responseContent（已完成时的完整结果）、errorMsg（失败原因）、
     *         createTime、updateTime 等
     */
    @GetMapping("ai-writing/task/{taskId}")
    @Override
    public Response<AiTaskDetailResponseDTO> queryTaskDetail(@PathVariable Long taskId) {
        try {
            AiTaskEntity task = aiWritingService.queryTask(taskId, DEFAULT_USER_ID);
            return Response.<AiTaskDetailResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toDetailDTO(task))
                    .build();
        } catch (Exception e) {
            log.error("查询 AI 任务详情失败 taskId:{}", taskId, e);
            return fail(e);
        }
    }

    /**
     * SSE（Server-Sent Events）流式获取 AI 写作生成内容
     *
     * <p>使用场景：用户在编辑器中使用"AI 写作"生成长文本（如一篇文章、一个章节），前端打开此 SSE 连接，
     * AI 模型逐字/逐句生成内容并通过事件流实时推送到前端，前端实时渲染到编辑器中，给用户"边看边写"的体验。</p>
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>前端创建 EventSource 连接到此接口，传入 taskId</li>
     *   <li>后端在异步线程中调用 AI 模型生成内容，通过回调将每个生成片段封装为 {@link AiWritingStreamEventDTO} 推送给前端</li>
     *   <li>事件流的 phase 字段标识当前阶段：{@code start}（开始）、{@code streaming}（生成中）、{@code done}（完成）、{@code error}（异常）</li>
     *   <li>生成完成时 emitter.complete() 关闭连接；发生异常时发送 error 事件后 emitter.completeWithError()</li>
     * </ol>
     *
     * <p>SSE 超时时间：5 分钟，超出后连接自动断开。</p>
     *
     * @param taskId 任务 ID，由 {@link #submitTask} 返回
     * @return ResponseBodyEmitter，以 text/event-stream 格式推送 {@link AiWritingStreamEventDTO} 序列化后的 JSON 数据
     */
    @GetMapping(value = "ai-writing/task/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter stream(@RequestParam Long taskId) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(10 * 60 * 1000L);
        AtomicBoolean completed = new AtomicBoolean(false);

        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> {
            completed.set(true);
            log.warn("AI 写作 SSE 连接超时 taskId:{}", taskId);
        });

        CompletableFuture.runAsync(() -> {
            try {
                aiWritingService.generateStream(taskId, DEFAULT_USER_ID, event -> {
                    if (completed.get()) {
                        return;
                    }
                    sendEvent(emitter, toStreamDTO(event));
                });
                if (!completed.get()) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("AI 写作流式生成失败 taskId:{}", taskId, e);
                if (!completed.get()) {
                    sendEvent(emitter, AiWritingStreamEventDTO.builder()
                            .phase("error")
                            .chunk(AiWritingChunkDTO.builder()
                                    .type("error")
                                    .content(null == e.getMessage() ? "AI 写作流式生成失败" : e.getMessage())
                                    .build())
                            .build());
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    /**
     * 查询草稿关联的最近 AI 任务列表
     */
    @GetMapping("ai-writing/task/list")
    public Response<List<AiTaskDetailResponseDTO>> queryTaskList(@RequestParam Long draftId,
                                                                  @RequestParam(defaultValue = "5") int limit) {
        try {
            List<AiTaskEntity> tasks = aiWritingService.queryTaskList(draftId, DEFAULT_USER_ID, limit);
            List<AiTaskDetailResponseDTO> data = tasks.stream()
                    .map(this::toDetailDTO)
                    .collect(Collectors.toList());
            return Response.<List<AiTaskDetailResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            log.error("查询 AI 任务列表失败 draftId:{}", draftId, e);
            return fail(e);
        }
    }

    /**
     * 向 SSE 连接发送事件，忽略客户端已断开导致的异常
     */
    private void sendEvent(ResponseBodyEmitter emitter, AiWritingStreamEventDTO event) {
        try {
            emitter.send("data: " + JSON.toJSONString(event) + "\n\n", MediaType.TEXT_EVENT_STREAM);
        } catch (IOException e) {
            log.warn("SSE 发送事件时连接已断开，忽略此事件: phase={}", event.getPhase());
        }
    }

    /**
     * 将领域层的流事件 VO 转换为 API 响应 DTO
     *
     * <p>领域层 {@link AiWritingStreamEventVO} 是内部模型，包含 phase 和 chunk 属性；
     * 此方法将其转换为前端可消费的 {@link AiWritingStreamEventDTO}，避免领域层模型直接暴露到 API 层。</p>
     *
     * <p>转换规则：直接映射 phase，将 chunk 中的 type/content/raw 逐字段拷贝，
     * 空值安全（chunk 为 null 时 chunk 字段置 null 而非 NPE）。</p>
     *
     * @param event 领域层的流事件值对象
     * @return API 层的流事件 DTO，用于序列化后通过 SSE 推送给前端
     */
    private AiWritingStreamEventDTO toStreamDTO(AiWritingStreamEventVO event) {
        return AiWritingStreamEventDTO.builder()
                .phase(event.getPhase())
                .chunk(AiWritingChunkDTO.builder()
                        .type(event.getChunk() != null ? event.getChunk().getType() : null)
                        .content(event.getChunk() != null ? event.getChunk().getContent() : null)
                        .raw(event.getChunk() != null ? event.getChunk().getRaw() : null)
                        .build())
                .build();
    }

    /**
     * 将领域层 AiTaskEntity 转换为提交任务响应的 DTO
     *
     * <p>在 {@link #submitTask} 中调用，将 AI 任务实体中的关键字段映射为前端需要的提交响应格式。
     * 前端通过返回的 taskId 后续调用 {@link #queryTaskDetail} 或 {@link #stream}。</p>
     *
     * @param task 领域层的 AI 任务实体
     * @return 提交响应 DTO，包含 taskId、draftId、taskType、status、statusDesc
     */
    private SubmitAiTaskResponseDTO toSubmitDTO(AiTaskEntity task) {
        return SubmitAiTaskResponseDTO.builder()
                .taskId(task.getTaskId())
                .draftId(task.getDraftId())
                .taskType(task.getTaskType().getCode())
                .status(task.getStatus().getCode())
                .statusDesc(task.getStatus().getDesc())
                .build();
    }

    /**
     * 将领域层 AiTaskEntity 转换为任务详情响应的 DTO
     *
     * <p>在 {@link #queryTaskDetail} 中调用，将 AI 任务实体的全部字段映射为前端需要的详情格式，
     * 包括任务的当前状态、生成结果、错误信息和创建时间等。</p>
     *
     * <p>使用场景：前端轮询或单次查询任务详情时，通过此 DTO 获取完整信息：
     * <ul>
     *   <li>任务进行中：status=processing，responseContent 可能为部分片段</li>
     *   <li>任务完成：status=completed，responseContent 为完整生成结果</li>
     *   <li>任务失败：status=failed，errorMsg 为失败原因</li>
     * </ul>
     * </p>
     *
     * @param task 领域层的 AI 任务实体
     * @return 任务详情 DTO，包含 taskId、draftId、taskType、status、statusDesc、responseContent、
     *         errorMsg、createTime、updateTime
     */
    private AiTaskDetailResponseDTO toDetailDTO(AiTaskEntity task) {
        return AiTaskDetailResponseDTO.builder()
                .taskId(task.getTaskId())
                .draftId(task.getDraftId())
                .taskType(task.getTaskType().getCode())
                .status(task.getStatus().getCode())
                .statusDesc(task.getStatus().getDesc())
                .responseContent(task.getResponseContent())
                .errorMsg(task.getErrorMsg())
                .createTime(task.getCreateTime() != null ? task.getCreateTime().format(DTF) : null)
                .updateTime(task.getUpdateTime() != null ? task.getUpdateTime().format(DTF) : null)
                .build();
    }

    /**
     * 构造统一的失败响应
     *
     * <p>统一异常处理辅助方法，将 Controller 层捕获的异常转换为标准的 {@link Response} 失败响应。
     * 如果异常是 {@link AppException} 类型，则提取其中的 code 和 info 作为错误码和错误信息；
     * 否则使用默认的 {@link ResponseCode#UN_ERROR}。</p>
     *
     * <p>使用场景：当前类中 {@link #submitTask} 和 {@link #queryTaskDetail} 的 catch 块中调用，
     * 将业务异常或系统异常包装为统一的响应格式返回给前端。</p>
     *
     * @param e 捕获到的异常对象
     * @param <T> 响应数据类型
     * @return 统一失败响应，包含错误码和错误信息
     */
    private <T> Response<T> fail(Exception e) {
        String code = ResponseCode.UN_ERROR.getCode();
        String info = e.getMessage();
        if (e instanceof AppException ae) {
            code = ae.getCode() != null ? ae.getCode() : code;
            info = ae.getInfo() != null ? ae.getInfo() : info;
        }
        return Response.<T>builder().code(code).info(info).build();
    }
}
