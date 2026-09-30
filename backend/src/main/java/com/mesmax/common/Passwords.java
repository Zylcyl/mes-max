package com.mesmax.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 口令校验与哈希。
 *
 * <p>历史包袱与迁移策略：最初的种子数据里口令是明文（`mes123456`），
 * 现在统一用 BCrypt 存储。为了让老数据仍能登录，{@link #matches} 对「非 BCrypt 格式」的
 * 存量口令做等值比较；登录成功后由调用方立刻把明文替换成 BCrypt 哈希（见 AuthController）。
 * 也就是说**只要用户登录一次，这条明文就会自动消失**。
 *
 * <p>生产环境还应当把这个兼容分支删掉 —— 它的存在只是为了演示数据能平滑升级。
 */
public final class Passwords {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** BCrypt 哈希的形态：$2a$10$... / $2b$... / $2y$... */
    private static final String BCRYPT_PATTERN = "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$";

    private Passwords() {
    }

    public static String hash(String raw) {
        if (raw == null || raw.isEmpty()) throw new BizException("口令不能为空");
        return ENCODER.encode(raw);
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.matches(BCRYPT_PATTERN);
    }

    /**
     * 校验口令。
     *
     * @param raw    用户提交的明文
     * @param stored 库里存的（BCrypt 哈希或历史明文）
     */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null || stored.isEmpty()) return false;
        if (isHashed(stored)) return ENCODER.matches(raw, stored);
        // 兜底：历史明文口令，用等时比较避免长度泄漏
        return constantTimeEquals(raw, stored);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
