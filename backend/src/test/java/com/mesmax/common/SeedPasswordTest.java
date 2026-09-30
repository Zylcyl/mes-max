package com.mesmax.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 种子数据口令形态守卫。
 *
 * <p>为什么值得单独写一个测试：{@code sql/seed-data.sql} 里的口令字面量一旦写错
 * （少一位、把哈希写成明文、复制错行），表现是「演示环境所有账号都登不进去」，
 * 而这个文件不在编译单元里，编译器不会给任何提示。这里把手写的哈希拿去和
 * {@code mes123456} 做一次真实校验，等于把「演示账号可用」变成构建期断言。
 */
class SeedPasswordTest {

    /** README / 登录页里公开的演示口令。 */
    private static final String DEMO_PASSWORD = "mes123456";

    @Test
    @DisplayName("种子数据里的账号口令是 BCrypt 哈希，且对应演示口令")
    void seedStoresBcryptHash() throws Exception {
        Path seed = locateSeedData();
        assumeTrue(seed != null, "找不到 sql/seed-data.sql（仅在完整仓库布局下校验）");

        String sql = Files.readString(seed, StandardCharsets.UTF_8);
        int userInsert = sql.indexOf("INSERT INTO sys_user");
        assertTrue(userInsert > 0, "seed-data.sql 里应当有 sys_user 的插入语句");
        String userSection = sql.substring(userInsert, sql.indexOf(';', userInsert));

        assertFalse(userSection.contains("'" + DEMO_PASSWORD + "'"),
                "种子数据里不应再出现明文口令：" + DEMO_PASSWORD);

        List<String> hashes = extractQuotedValues(userSection).stream()
                .filter(Passwords::isHashed)
                .distinct()
                .toList();
        assertFalse(hashes.isEmpty(), "sys_user 的口令列应当是 BCrypt 哈希（$2a$/$2b$/$2y$ 开头）");
        for (String h : hashes) {
            assertTrue(Passwords.matches(DEMO_PASSWORD, h),
                    "种子里的哈希必须能校验通过演示口令，否则演示账号全部登不进去：" + h);
        }
    }

    @Test
    @DisplayName("业务异常默认是 400（客户端可纠正），不是 500")
    void bizExceptionDefaultsToBadRequest() {
        assertEquals(400, new BizException("参数不合法").getCode());
        assertEquals(409, new BizException(BizException.CONFLICT, "状态冲突").getCode());
    }

    /** 在一串 SQL 片段里取出所有单引号字符串字面量。 */
    private static List<String> extractQuotedValues(String text) {
        Matcher m = Pattern.compile("'([^']*)'").matcher(text);
        List<String> values = new java.util.ArrayList<>();
        while (m.find()) values.add(m.group(1));
        return values;
    }

    /** 兼容「从 backend/ 跑测试」与「从仓库根跑测试」两种工作目录。 */
    private static Path locateSeedData() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = cwd;
        for (int i = 0; i < 4 && cursor != null; i++) {
            Path candidate = cursor.resolve("sql/seed-data.sql");
            if (Files.isRegularFile(candidate)) return candidate;
            cursor = cursor.getParent();
        }
        return null;
    }
}
