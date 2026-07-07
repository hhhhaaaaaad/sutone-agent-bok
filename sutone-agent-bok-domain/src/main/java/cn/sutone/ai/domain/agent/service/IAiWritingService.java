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

    /**
     * 提交 AI 写作任务
     */
    AiTaskEntity submitTask(Long userId, Long draftId, String taskTypeCode, Map<String, Object> promptParams);

    /**
     * 查询任务详情
     */
    AiTaskEntity queryTask(Long taskId, Long userId);

    /**
     * 流式生成写作内容
     */
    void generateStream(Long taskId, Long userId, Consumer<AiWritingStreamEventVO> eventConsumer);

    /**
     * 查询草稿关联的最近任务列表
     */
    List<AiTaskEntity> queryTaskList(Long draftId, Long userId, int limit);

}
