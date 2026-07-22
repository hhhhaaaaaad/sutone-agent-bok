package cn.sutone.ai.domain.agent.service.ai_writing;

/**
 * 可重试的 Agent 异常（网络超时、API 限流、临时 5xx 等）
 * Consumer 捕获此异常后会重新抛出，触发 RocketMQ 重试
 */
public class RetryableAgentException extends RuntimeException {

    public RetryableAgentException(String message) {
        super(message);
    }

    public RetryableAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
