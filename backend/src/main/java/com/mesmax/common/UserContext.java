package com.mesmax.common;

import java.util.Map;

/** 当前登录用户上下文（由 AuthInterceptor 填充，请求结束清理）。 */
public final class UserContext {

    private static final ThreadLocal<Map<String, Object>> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Map<String, Object> claims) {
        HOLDER.set(claims);
    }

    public static Map<String, Object> get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static String username() {
        return JwtUtil.str(HOLDER.get(), "sub");
    }

    public static String realName() {
        return JwtUtil.str(HOLDER.get(), "name");
    }

    public static String role() {
        return JwtUtil.str(HOLDER.get(), "role");
    }

    public static Long userId() {
        return JwtUtil.userId(HOLDER.get());
    }

    public static boolean isAdmin() {
        return "admin".equals(role());
    }
}
