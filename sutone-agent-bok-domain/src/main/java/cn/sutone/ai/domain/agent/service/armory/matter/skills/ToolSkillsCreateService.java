package cn.sutone.ai.domain.agent.service.armory.matter.skills;

import cn.sutone.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

/**
 * 工具 skills 构建服务
 *
 */
public interface ToolSkillsCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills) throws Exception;

}
