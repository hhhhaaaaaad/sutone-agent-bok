package cn.sutone.ai.api.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {

    private String agentId;
    private String userId;
    private String sessionId;
    private String message;

    // 自定义配置
    private String customBaseUrl;
    private String customApiKey;
    private String customCompletionsPath;
    private String customModel;

}
