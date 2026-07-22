package cn.sutone.ai.domain.agent.service;

import cn.sutone.ai.domain.agent.model.valobj.AiWritingStreamEventVO;

import java.util.List;
import java.util.Map;

/**
 * 任务事件发布/读取接口 (领域层定义，基础设施层实现)
 */
public interface ITaskEventPublisher {

    void publish(Long taskId, AiWritingStreamEventVO event);

    void publishStatus(Long taskId, String phase, String content);

    void publishToken(Long taskId, String phase, String content);

    void publishDone(Long taskId);

    void publishError(Long taskId, String errorMsg);

    /**
     * 从 Redis Stream 读取事件，用于 SSE 补读/订阅
     * @return [streamId, {phase, type, content}]
     */
    List<Map.Entry<String, Map<String, String>>> readEvents(Long taskId, String lastEventId);
}
