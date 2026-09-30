package com.mesmax.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 JWT（HS256）实现 —— 不引入任何第三方依赖。
 *
 * <p>之所以自己实现而非引入 jjwt：MES 部署在车间内网，鉴权需求极简（签发 / 校验 / 取声明），
 * 少一个依赖就少一个内网离线升级的麻烦。
 */
public final class JwtUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtUtil.class);

    /** 本机密钥文件名（相对启动目录）。可用 -Dmesmax.jwt.secretFile=... 指定其它位置。 */
    private static final String SECRET_FILE = System.getProperty("mesmax.jwt.secretFile", ".jwt-secret");

    /**
     * 令牌签名密钥。
     *
     * <p>声明顺序很重要：它必须在 {@link #SECRET_FILE} 之后初始化 ——
     * 静态字段按书写顺序求值，若把它写在前面，resolveSecret() 读到的会是 null。
     */
    private static final String SECRET = resolveSecret();

    /** 令牌有效期：12 小时（覆盖一个完整班次）。 */
    private static final long TTL_MILLIS = 12 * 60 * 60 * 1000L;

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private JwtUtil() {
    }

    /**
     * 主动触发静态初始化。
     *
     * <p>类中的密钥解析是静态初始化的一部分，若不主动触发，它会等到「第一次签发/校验令牌」
     * 才执行 —— 那样密钥文件要等第一次登录才出现，容易让人误以为没生效。
     * 启动时调用一次，密钥来源会在启动日志里立刻可见。
     */
    public static void ensureInitialized() {
        if (SECRET == null || SECRET.isEmpty()) {
            throw new IllegalStateException("令牌密钥初始化失败");
        }
    }

    /**
     * 解析签名密钥，优先级：环境变量 → 本机密钥文件 → 生成并落盘。
     *
     * <p>为什么刻意**不提供写死的默认密钥**：一旦默认值进了代码或部署文件，
     * 任何人都能用它自签一个 role=admin 的令牌（甚至不带 exp 做成永久令牌），
     * 车间内网也一样能被攻破。
     *
     * <p>为什么做了「生成后落盘」这一步，而不是每次启动都随机：车间演示/答辩现场
     * 经常重启后端，每次重启都把所有人踢下线是没必要的折腾。把密钥写进
     * 工作目录下的 {@code .jwt-secret}（已加入 .gitignore），既保证不进入版本库，
     * 又能让登录态跨重启保持有效。生产环境应当显式配置 MES_JWT_SECRET。
     */
    private static String resolveSecret() {
        String env = System.getenv("MES_JWT_SECRET");
        if (env != null && !env.trim().isEmpty()) {
            String value = env.trim();
            if (value.length() < 16) {
                log.warn("环境变量 MES_JWT_SECRET 长度不足 16 位，建议更换为强随机串（openssl rand -hex 32）");
            }
            log.info("令牌密钥来源：环境变量 MES_JWT_SECRET");
            return value;
        }

        Path file = Paths.get(SECRET_FILE);
        try {
            if (Files.isRegularFile(file)) {
                String saved = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (saved.length() >= 16) {
                    log.info("令牌密钥来源：本机密钥文件 {}（重启后登录态保持有效）", file.toAbsolutePath());
                    return saved;
                }
                log.warn("{} 内容过短（{} 位），将重新生成", file.toAbsolutePath(), saved.length());
            }
            String generated = generate();
            Files.writeString(file, generated, StandardCharsets.UTF_8);
            restrict(file);
            log.warn("未配置 MES_JWT_SECRET，已在 {} 生成并保存本机令牌密钥；"
                    + "该文件已被 .gitignore 忽略，切勿提交。生产部署请显式配置环境变量。",
                    file.toAbsolutePath());
            return generated;
        } catch (Exception e) {
            log.warn("无法读写令牌密钥文件 {}（{}），本次改用内存随机密钥，重启后需重新登录",
                    file.toAbsolutePath(), e.getMessage());
            return generate();
        }
    }

    /** 生成 32 字节随机密钥（Base64 后 44 字符）。 */
    private static String generate() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        return Base64.getEncoder().encodeToString(buf);
    }

    /** 尽量收紧密钥文件权限（Windows 上不支持 POSIX 权限，忽略即可）。 */
    private static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // 非 POSIX 文件系统：跳过
        }
    }

    public static String sign(Long userId, String username, String realName, String roleCode) {
        long exp = System.currentTimeMillis() + TTL_MILLIS;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("uid", userId);
        claims.put("sub", username);
        claims.put("name", realName == null ? username : realName);
        claims.put("role", roleCode);
        claims.put("exp", exp);
        return signClaims(claims);
    }

    /**
     * 按给定声明签发令牌。
     *
     * <p>包内可见：单元测试要用它构造「没有 exp」「exp 已过期」这类非法令牌，
     * 验证 verify() 会拒绝 —— 这类用例没法通过公开 API 造出来。
     */
    static String signClaims(Map<String, Object> claims) {
        String header = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = ENC.encodeToString(toJson(claims).getBytes(StandardCharsets.UTF_8));
        String body = header + "." + payload;
        return body + "." + ENC.encodeToString(hmac(body));
    }

    /** 校验签名与有效期，通过则返回声明，否则返回 null。 */
    public static Map<String, Object> verify(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        String body = parts[0] + "." + parts[1];
        byte[] expected = hmac(body);
        byte[] actual;
        try {
            actual = DEC.decode(parts[2]);
        } catch (Exception e) {
            return null;
        }
        if (!constantTimeEquals(expected, actual)) return null;
        Map<String, Object> claims = parseJson(new String(DEC.decode(parts[1]), StandardCharsets.UTF_8));
        Object exp = claims.get("exp");
        // 没有 exp 或 exp 不是数字的令牌一律拒绝：否则「不带 exp」就等价于「永不过期」
        if (!(exp instanceof Number n)) return null;
        if (n.longValue() < System.currentTimeMillis()) return null;
        return claims;
    }

    public static Long userId(Map<String, Object> claims) {
        Object v = claims == null ? null : claims.get("uid");
        return v instanceof Number n ? n.longValue() : null;
    }

    public static String str(Map<String, Object> claims, String key) {
        Object v = claims == null ? null : claims.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }

    /** 只处理本类产生的扁平 JSON（字符串/数字），避免引入 JSON 库。 */
    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) sb.append(v);
            else sb.append('"').append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    private static Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        int i = 0;
        int n = json.length();
        while (i < n) {
            int ks = json.indexOf('"', i);
            if (ks < 0) break;
            int ke = json.indexOf('"', ks + 1);
            if (ke < 0) break;
            String key = json.substring(ks + 1, ke);
            int colon = json.indexOf(':', ke);
            if (colon < 0) break;
            int vs = colon + 1;
            while (vs < n && Character.isWhitespace(json.charAt(vs))) vs++;
            Object value;
            if (vs < n && json.charAt(vs) == '"') {
                int ve = json.indexOf('"', vs + 1);
                if (ve < 0) break;
                value = json.substring(vs + 1, ve);
                i = ve + 1;
            } else {
                int ve = vs;
                while (ve < n && json.charAt(ve) != ',' && json.charAt(ve) != '}') ve++;
                String raw = json.substring(vs, ve).trim();
                try {
                    value = raw.contains(".") ? (Object) Double.parseDouble(raw) : (Object) Long.parseLong(raw);
                } catch (NumberFormatException ex) {
                    value = raw;
                }
                i = ve;
            }
            map.put(key, value);
            int next = json.indexOf(',', i);
            if (next < 0) break;
            i = next + 1;
        }
        return map;
    }
}
