package com.evernox.security;

import com.evernox.common.ResultCode;
import com.evernox.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具。
 *
 * JwtAuthenticationFilter 把 userId(Long) 作为 principal，管理端接口据此做数据归属校验。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 公开接口需要区分匿名/登录时使用 */
    public static Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}

