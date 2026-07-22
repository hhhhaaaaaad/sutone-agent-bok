package cn.sutone.ai.domain.agent.service;

import cn.sutone.ai.domain.agent.model.entity.AiTaskEntity;
import cn.sutone.ai.domain.agent.model.valobj.AiWritingStreamEventVO;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 写作服务接口
 */
public interface IAiWritingService {

    AiTaskEntity submitTask(Long userId, Long draftId, String taskTypeCode, Map<String, Object> promptParams, Boolean enableIllustration);

    AiTaskEntity queryTask(Long taskId, Long userId);

    void generateStream(Long taskId, Long userId, Consumer<AiWritingStreamEventVO> eventConsumer);

    List<AiTaskEntity> queryTaskList(Long draftId, Long userId, int limit);

    /**
     * MQ Consumer 调用：执行任务（不依赖 HTTP/Servlet）
     */
    void executeTask(Long taskId);
}
