package com.mesmax.boot;

import com.mesmax.common.PermRegistry;
import com.mesmax.common.Passwords;
import com.mesmax.entity.SysRole;
import com.mesmax.entity.SysUser;
import com.mesmax.mapper.SysRoleMapper;
import com.mesmax.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库引导：
 * <ol>
 *   <li>执行 {@code sql/schema.sql}（全部 IF NOT EXISTS，可反复执行）</li>
 *   <li>若 {@code sys_user} 为空，执行 {@code sql/seed-data.sql} 灌入 CNC 车间主数据</li>
 *   <li>把角色权限装载进 {@link PermRegistry}</li>
 * </ol>
 *
 * <p>SQL 脚本以 <b>项目根目录 sql/ </b>为唯一来源，避免与 resources 下的副本产生漂移；
 * 若作为 jar 独立部署而找不到脚本，会给出明确的报错提示。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SqlBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SqlBootstrap.class);

    private final DataSource dataSource;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    public SqlBootstrap(DataSource dataSource, SysUserMapper userMapper, SysRoleMapper roleMapper) {
        this.dataSource = dataSource;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            bootstrap();
        } catch (Exception e) {
            explainIfDatabaseAuthFailure(e);
            throw e;
        }
    }

    /**
     * H2 的「库文件口令」在创建时就写进文件头：如果 data/ 下的库是用旧口令（例如空口令）建的，
     * 换了配置后再启动只会看到一句 Hikari 连接失败的堆栈，很难定位。
     * 这里把它翻译成人话，并给出可执行的处理办法。
     */
    private void explainIfDatabaseAuthFailure(Exception e) {
        String msg = String.valueOf(e.getMessage()) + " " + String.valueOf(e.getCause() == null ? "" : e.getCause().getMessage());
        if (msg.contains("Wrong user name or password") || msg.contains("wrong user name or password")) {
            log.error("""
                    数据库口令与现有库文件不匹配：data/ 下的 H2 库是在「另一个口令」下创建的。
                    处理办法（任选其一）：
                      1) 演示环境：删除 backend/data 目录后重启，会自动重建库并灌入演示数据；
                      2) 保留数据：把 spring.datasource.password 改回创建该库时使用的口令；
                      3) 生产环境：请改用 MySQL（--spring.profiles.active=mysql），不要用 H2 文件库。
                    """);
        }
    }

    private void bootstrap() throws Exception {
        Path schema = locate("schema.sql");
        Path seed = locate("seed-data.sql");

        // 可选的重置开关：java -Dmesmax.db.reset=true（或 --mesmax.db.reset=true）
        // 用途：演示环境需要回到干净初始状态时，不必手工去删 H2 数据文件。
        if (Boolean.parseBoolean(System.getProperty("mesmax.db.reset", "false"))) {
            log.warn("检测到 mesmax.db.reset=true，正在清空数据库全部对象…");
            try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
                st.execute("DROP ALL OBJECTS");
            }
            log.warn("数据库已清空，将重新建表并灌入种子数据");
        }

        long t0 = System.currentTimeMillis();
        logDatabaseTarget();
        int n1 = executeScript(schema);
        log.info("建表脚本执行完成：{} 条语句（{} ms）", n1, System.currentTimeMillis() - t0);

        Long userCount = userMapper.selectCount(null);
        if (userCount == null || userCount == 0) {
            long t1 = System.currentTimeMillis();
            int n2 = executeScript(seed);
            log.info("空库检测通过，灌入 CNC 车间主数据：{} 条语句（{} ms）", n2, System.currentTimeMillis() - t1);
        } else {
            log.info("检测到已有数据（用户 {} 条），跳过主数据初始化", userCount);
        }

        warnIfPlaintextPasswords();

        for (SysRole role : roleMapper.selectList(null)) {
            PermRegistry.put(role.getRoleCode(), role.getPerms());
        }
        log.info("角色权限已装载：{} 个角色", PermRegistry.all().size());
    }

    /**
     * 把「这次到底连到了哪个库」写进启动日志。
     *
     * <p>H2 的连接串是相对路径（{@code ./data/mesmax}），从项目根启动和从 {@code backend/}
     * 启动会落到两个不同的目录，现场经常出现「明明灌了数据却查不到」的困惑；
     * 启动日志里打一行绝对路径，五秒就能看出来。
     */
    private void logDatabaseTarget() {
        try (Connection conn = dataSource.getConnection()) {
            var md = conn.getMetaData();
            log.info("数据库：{} {} | {}", md.getDatabaseProductName(), md.getDatabaseProductVersion(),
                    redact(md.getURL()));
        } catch (Exception e) {
            log.warn("读取数据库元信息失败：{}", e.getMessage());
        }
    }

    /** 连接串里若带口令参数，日志里必须打码。 */
    private static String redact(String url) {
        if (url == null) return "-";
        return url.replaceAll("(?i)(password|pwd)=[^;&]*", "$1=***");
    }

    /**
     * 明文口令体检。
     *
     * <p>种子脚本已改为直接写入 BCrypt 哈希；但历史库（以及从旧版本升上来的库）里可能还留着明文。
     * 这里只告警不改写 —— 改口令是业务动作，登录时的自动升级才是那条既定路径。
     */
    private void warnIfPlaintextPasswords() {
        List<String> plain = new ArrayList<>();
        for (SysUser u : userMapper.selectList(null)) {
            if (!Passwords.isHashed(u.getPassword())) plain.add(u.getUsername());
        }
        if (!plain.isEmpty()) {
            log.warn("检测到 {} 个账号的口令仍以明文存储（{}）：这些账号首次登录成功后会自动升级为 BCrypt 哈希，"
                    + "生产环境请立即改掉初始口令。", plain.size(), String.join(", ", plain));
        }
    }

    /** 在若干候选目录中定位 SQL 脚本（兼容「从 backend/ 启动」与「从项目根启动」两种方式）。 */
    private Path locate(String fileName) {
        List<Path> candidates = new ArrayList<>();
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = cwd;
        for (int i = 0; i < 4 && cursor != null; i++) {
            candidates.add(cursor.resolve("sql").resolve(fileName));
            candidates.add(cursor.resolve("backend/sql").resolve(fileName));
            cursor = cursor.getParent();
        }
        candidates.add(cwd.resolve("src/main/resources/db").resolve(fileName));

        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                log.info("SQL 脚本命中：{}", p);
                return p;
            }
        }
        throw new IllegalStateException("找不到 SQL 脚本 " + fileName + "，已尝试：\n  "
                + String.join("\n  ", candidates.stream().map(Path::toString).toList()));
    }

    /**
     * 极简 SQL 脚本执行器。
     *
     * <p>不使用 Spring 的 {@code ScriptUtils}：因为 MySQL 8 不支持
     * {@code CREATE INDEX IF NOT EXISTS}，而 H2 支持。这里对索引语句的失败做容错，
     * 使同一份脚本既能跑 H2 也能跑 MySQL。
     */
    private int executeScript(Path path) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        StringBuilder cleaned = new StringBuilder();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
            cleaned.append(line).append('\n');
        }

        int executed = 0;
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            // 整个脚本放在一个事务里：中途失败会整体回滚，避免出现「灌了一半」的半成品数据
            // （注意：MySQL 的 DDL 会隐式提交，事务保护主要对 H2 与 INSERT 批次生效）
            conn.setAutoCommit(false);
            String dialect = conn.getMetaData().getDatabaseProductName();
            boolean mysql = dialect != null && dialect.toLowerCase().contains("mysql");
            int idxOk = 0;
            int idxExists = 0;
            int idxFailed = 0;
            try {
                for (String sql : cleaned.toString().split(";")) {
                    String s = sql.trim();
                    if (s.isEmpty()) continue;

                    /*
                     * MySQL 8 不支持 CREATE INDEX IF NOT EXISTS（那是 H2 的语法）。
                     * 早先的写法是「只要是 CREATE INDEX 失败就当成索引已存在默默跳过」——
                     * 结果在 MySQL 上 7 条索引全部因语法错误被静默吞掉，
                     * 生产库跑起来一条二级索引都没有，性能问题要等出事故才发现。
                     * 现在的做法：MySQL 下先剥掉 IF NOT EXISTS，再区分「重复」与「真失败」。
                     */
                    boolean isCreateIndex = s.toUpperCase().startsWith("CREATE INDEX");
                    String stmt = s;
                    if (mysql && isCreateIndex) {
                        stmt = s.replaceFirst("(?i)IF\\s+NOT\\s+EXISTS\\s+", "");
                    }
                    try {
                        st.execute(stmt);
                        executed++;
                        if (isCreateIndex) idxOk++;
                    } catch (Exception e) {
                        String msg = String.valueOf(e.getMessage()).toLowerCase();
                        boolean alreadyExists = msg.contains("already exists") || msg.contains("duplicate");
                        if (isCreateIndex && alreadyExists) {
                            idxExists++;
                            log.debug("索引已存在，跳过：{}", e.getMessage());
                        } else if (isCreateIndex) {
                            // 语法错误 / 权限不足必须喊出来，不能静默吞掉
                            idxFailed++;
                            log.warn("索引创建失败（{}）：{}\nSQL: {}", dialect, e.getMessage(), stmt);
                        } else {
                            conn.rollback();
                            throw new IllegalStateException("执行 SQL 失败：\n" + s + "\n原因：" + e.getMessage(), e);
                        }
                    }
                }
                conn.commit();
                if (idxOk + idxExists + idxFailed > 0) {
                    // 注意用词：H2 的 CREATE INDEX IF NOT EXISTS 对已存在的索引也返回成功，
                    // 所以这里是「执行成功」而不是「新建」，避免把重复执行说成重复创建
                    log.info("索引处理完成（{}）：执行成功 {}，判定为已存在 {}，失败 {}",
                            dialect, idxOk, idxExists, idxFailed);
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("SQL 脚本执行异常：" + path + " → " + e.getMessage(), e);
        }
        return executed;
    }
}
