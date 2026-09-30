package com.mesmax.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    @Test
    @DisplayName("正常签发与校验：声明可还原")
    void signAndVerify() {
        String token = JwtUtil.sign(1L, "admin", "系统管理员", "admin");
        Map<String, Object> claims = JwtUtil.verify(token);
        assertNotNull(claims);
        assertEquals(1L, ((Number) claims.get("uid")).longValue());
        assertEquals("admin", claims.get("sub"));
        assertEquals("系统管理员", claims.get("name"));
        assertEquals("admin", claims.get("role"));
    }

    @Test
    @DisplayName("篡改载荷后签名不匹配，必须拒绝")
    void tamperedPayloadRejected() {
        String token = JwtUtil.sign(2L, "planner", "周计划", "planner");
        String[] parts = token.split("\\.");
        // 把 role 改成 admin 再重新编码（不重签名）
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"planner\"", "\"admin\"");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];
        assertNull(JwtUtil.verify(forged), "改了载荷却没重签名，令牌必须被拒");
    }

    @Test
    @DisplayName("没有 exp 的令牌必须拒绝（否则等同永不过期）")
    void tokenWithoutExpRejected() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("uid", 1L);
        claims.put("sub", "admin");
        claims.put("role", "admin");
        assertNull(JwtUtil.verify(JwtUtil.signClaims(claims)));
    }

    @Test
    @DisplayName("exp 已过期的令牌必须拒绝")
    void expiredTokenRejected() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("uid", 1L);
        claims.put("sub", "admin");
        claims.put("role", "admin");
        claims.put("exp", System.currentTimeMillis() - 1000);
        assertNull(JwtUtil.verify(JwtUtil.signClaims(claims)));
    }

    @Test
    @DisplayName("格式非法 / 空令牌不抛异常，只返回 null")
    void malformedTokenRejected() {
        assertNull(JwtUtil.verify(null));
        assertNull(JwtUtil.verify(""));
        assertNull(JwtUtil.verify("a.b"));
        assertNull(JwtUtil.verify("a.b.c"));
        assertNull(JwtUtil.verify("不是令牌"));
    }
}
