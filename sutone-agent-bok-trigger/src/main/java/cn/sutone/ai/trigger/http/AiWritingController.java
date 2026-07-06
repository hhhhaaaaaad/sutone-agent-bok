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
import java.util.concurrent.CompletableFuture;

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

    @GetMapping(value = "ai-writing/task/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter stream(@RequestParam Long taskId) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(5 * 60 * 1000L);
        CompletableFuture.runAsync(() -> {
            try {
                aiWritingService.generateStream(taskId, DEFAULT_USER_ID, event -> sendEvent(emitter, toStreamDTO(event)));
                emitter.complete();
            } catch (Exception e) {
                log.error("AI 写作流式生成失败 taskId:{}", taskId, e);
                sendEvent(emitter, AiWritingStreamEventDTO.builder()
                        .phase("error")
                        .chunk(AiWritingChunkDTO.builder()
                                .type("error")
                                .content(null == e.getMessage() ? "AI 写作流式生成失败" : e.getMessage())
                                .build())
                        .build());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendEvent(ResponseBodyEmitter emitter, AiWritingStreamEventDTO event) {
        try {
            emitter.send("data: " + JSON.toJSONString(event) + "\n\n", MediaType.TEXT_EVENT_STREAM);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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

    private SubmitAiTaskResponseDTO toSubmitDTO(AiTaskEntity task) {
        return SubmitAiTaskResponseDTO.builder()
                .taskId(task.getTaskId())
                .draftId(task.getDraftId())
                .taskType(task.getTaskType().getCode())
                .status(task.getStatus().getCode())
                .statusDesc(task.getStatus().getDesc())
                .build();
    }

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
