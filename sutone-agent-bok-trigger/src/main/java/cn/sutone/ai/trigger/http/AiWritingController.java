package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.IAiWritingService;
import cn.sutone.ai.api.dto.aiwriting.AiTaskDetailResponseDTO;
import cn.sutone.ai.api.dto.aiwriting.SubmitAiTaskRequestDTO;
import cn.sutone.ai.api.dto.aiwriting.SubmitAiTaskResponseDTO;
import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI 写作 Controller
 * <p>
 * 注意：当前为骨架，AI 任务领域（AiTaskEntity/IAiTaskDao/AiWritingDomainService 等）尚未实现。
 * 此处先暴露接口占位，后续 M3 阶段接入真实 AI 链路。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AiWritingController implements IAiWritingService {

    @PostMapping("ai-writing/task/submit")
    @Override
    public Response<SubmitAiTaskResponseDTO> submitTask(@RequestBody SubmitAiTaskRequestDTO requestDTO) {
        log.info("AI 写作任务提交暂未实现 draftId:{} taskType:{}", requestDTO.getDraftId(), requestDTO.getTaskType());
        return Response.<SubmitAiTaskResponseDTO>builder()
                .code(ResponseCode.E0001.getCode())
                .info("AI 写作链路尚未接入，将在 M3 阶段实现")
                .build();
    }

    @GetMapping("ai-writing/task/{taskId}")
    @Override
    public Response<AiTaskDetailResponseDTO> queryTaskDetail(@PathVariable Long taskId) {
        log.info("AI 任务详情查询暂未实现 taskId:{}", taskId);
        return Response.<AiTaskDetailResponseDTO>builder()
                .code(ResponseCode.E0001.getCode())
                .info("AI 写作链路尚未接入，将在 M3 阶段实现")
                .build();
    }
}
