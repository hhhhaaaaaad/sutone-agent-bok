package cn.sutone.ai.trigger.security;

import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 中获取当前登录用户的工具类
 */
public final class AuthUtil {

    private AuthUtil() {
    }

    public static Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        throw new AppException(ResponseCode.UNAUTHORIZED.getCode(), "未登录或 token 已过期");
    }

    public static Long getCurrentUserIdOrNull() {
        try {
            return getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
