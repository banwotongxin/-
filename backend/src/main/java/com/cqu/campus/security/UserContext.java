package com.cqu.campus.security;

import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;

/**
 * 基于 ThreadLocal 的当前用户上下文
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<CurrentUser>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_REQUIRED, "未登录或令牌无效");
        }
        return user;
    }

    public static Long getUserId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static boolean hasRole(String role) {
        CurrentUser user = HOLDER.get();
        return user != null && user.hasRole(role);
    }
}
