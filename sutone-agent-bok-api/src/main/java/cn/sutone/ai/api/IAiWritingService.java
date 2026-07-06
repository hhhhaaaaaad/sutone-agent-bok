package cn.sutone.ai.api;

import cn.sutone.ai.api.dto.aiwriting.*;
import cn.sutone.ai.api.response.Response;

/**
 * AI 写作服务接口
 */
public interface IAiWritingService {

    Response<SubmitAiTaskResponseDTO> submitTask(SubmitAiTaskRequestDTO requestDTO);

    Response<AiTaskDetailResponseDTO> queryTaskDetail(Long taskId);
}
