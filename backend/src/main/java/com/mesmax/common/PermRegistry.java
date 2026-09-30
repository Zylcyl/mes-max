package com.mesmax.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 角色 → 权限串缓存（登录时由 AuthService 刷新）。 */
public final class PermRegistry {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PermRegistry() {
    }

    public static void put(String roleCode, String perms) {
        if (roleCode != null) CACHE.put(roleCode, perms == null ? "" : perms);
    }

    public static String perms(String roleCode) {
        return roleCode == null ? null : CACHE.get(roleCode);
    }

    public static Map<String, String> all() {
        return CACHE;
    }
}
