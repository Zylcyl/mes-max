package com.mesmax.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordsTest {

    @Test
    @DisplayName("哈希后可校验，且同一口令两次哈希不同（有盐）")
    void hashAndMatch() {
        String h1 = Passwords.hash("mes123456");
        String h2 = Passwords.hash("mes123456");
        assertNotEquals(h1, h2, "BCrypt 应带随机盐");
        assertTrue(Passwords.isHashed(h1));
        assertTrue(Passwords.matches("mes123456", h1));
        assertTrue(Passwords.matches("mes123456", h2));
        assertFalse(Passwords.matches("wrong", h1));
    }

    @Test
    @DisplayName("历史明文口令仍可登录（供种子数据平滑升级）")
    void legacyPlaintextStillMatches() {
        assertFalse(Passwords.isHashed("mes123456"));
        assertTrue(Passwords.matches("mes123456", "mes123456"));
        assertFalse(Passwords.matches("other", "mes123456"));
    }

    @Test
    @DisplayName("空口令一律不通过")
    void blankRejected() {
        assertFalse(Passwords.matches(null, "x"));
        assertFalse(Passwords.matches("x", null));
        assertFalse(Passwords.matches("", ""));
        assertThrows(BizException.class, () -> Passwords.hash(""));
    }
}
