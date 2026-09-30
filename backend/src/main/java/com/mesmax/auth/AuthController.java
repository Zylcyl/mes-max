package com.mesmax.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mesmax.common.*;
import com.mesmax.entity.SysLoginLog;
import com.mesmax.entity.SysRole;
import com.mesmax.entity.SysUser;
import com.mesmax.mapper.SysLoginLogMapper;
import com.mesmax.mapper.SysRoleMapper;
import com.mesmax.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysLoginLogMapper loginLogMapper;

    /**
     * 登录失败节流：{@code 账号 → 失败时间戳队列}。
     *
     * <p>车间内网的账号口令是「弱口令 + 全员知道」的组合，没有节流时一个脚本几分钟就能
     * 把 mes123456 之外的口令试一遍。窗口内的失败次数超过阈值就临时拒绝该账号，
     * 成功登录后立即清零，避免正常用户被自己的手滑锁住。
     */
    private final Map<String, Deque<Long>> loginFailures = new ConcurrentHashMap<>();

    @Value("${mes-max.security.login-max-failures:8}")
    private int maxFailures;

    @Value("${mes-max.security.login-failure-window-seconds:300}")
    private long failureWindowSeconds;

    public AuthController(SysUserMapper userMapper, SysRoleMapper roleMapper, SysLoginLogMapper loginLogMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.loginLogMapper = loginLogMapper;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        if (req == null || req.username() == null || req.password() == null) {
            throw new BizException("请输入账号与密码");
        }
        String username = req.username().trim();

        if (isLockedOut(username)) {
            // 锁定期间不再比对口令（也顺带避免把 CPU 花在 BCrypt 上）
            writeLog(username, request, false, "登录失败次数过多，已临时锁定");
            throw new BizException(BizException.TOO_MANY_REQUESTS,
                    "登录失败次数过多，请稍后再试（约 " + Math.max(1, failureWindowSeconds / 60) + " 分钟）");
        }

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username).last("limit 1"));

        if (user == null || !Passwords.matches(req.password(), user.getPassword())) {
            recordFailure(username);
            writeLog(username, request, false, "账号或密码错误");
            throw new BizException(BizException.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            // 停用账号不能记「登录成功」：登录日志是审计证据，必须与实际结果一致
            writeLog(username, request, false, "账号已停用");
            throw new BizException(BizException.FORBIDDEN, "该账号已被停用");
        }
        clearFailures(username);
        writeLog(username, request, true, "登录成功");

        // 存量明文口令在首次成功登录时就地升级为 BCrypt 哈希，避免明文长期留在库里
        if (!Passwords.isHashed(user.getPassword())) {
            user.setPassword(Passwords.hash(req.password()));
        }

        SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, user.getRoleCode()).last("limit 1"));
        if (role != null) PermRegistry.put(role.getRoleCode(), role.getPerms());

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        String token = JwtUtil.sign(user.getId(), user.getUsername(), user.getRealName(), user.getRoleCode());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", safeUser(user));
        data.put("role", role);
        data.put("homePage", role == null ? "/board" : role.getHomePage());
        return R.ok("登录成功", data);
    }

    /** 窗口内的失败次数是否已达阈值；顺带清理过期时间戳。 */
    private boolean isLockedOut(String username) {
        Deque<Long> times = loginFailures.get(username);
        if (times == null) return false;
        long threshold = System.currentTimeMillis() - failureWindowSeconds * 1000L;
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < threshold) times.pollFirst();
            return times.size() >= maxFailures;
        }
    }

    private void recordFailure(String username) {
        if (loginFailures.size() > 2000) purgeFailures();
        Deque<Long> times = loginFailures.computeIfAbsent(username, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(System.currentTimeMillis());
        }
    }

    private void clearFailures(String username) {
        loginFailures.remove(username);
    }

    /** 防止被人用随机账号刷爆内存：清掉窗口内没有失败记录的条目。 */
    private void purgeFailures() {
        long threshold = System.currentTimeMillis() - failureWindowSeconds * 1000L;
        loginFailures.entrySet().removeIf(e -> {
            Deque<Long> times = e.getValue();
            synchronized (times) {
                while (!times.isEmpty() && times.peekFirst() < threshold) times.pollFirst();
                return times.isEmpty();
            }
        });
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok("已退出登录", null);
    }

    @GetMapping("/profile")
    public R<Map<String, Object>> profile() {
        Map<String, Object> m = new HashMap<>();
        m.put("username", UserContext.username());
        m.put("realName", UserContext.realName());
        m.put("roleCode", UserContext.role());
        m.put("userId", UserContext.userId());
        SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, UserContext.role()).last("limit 1"));
        m.put("role", role);
        m.put("perms", PermRegistry.perms(UserContext.role()));
        return R.ok(m);
    }

    @PostMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (newPwd == null || newPwd.length() < 6) throw new BizException("新密码长度不能少于 6 位");
        SysUser user = userMapper.selectById(UserContext.userId());
        if (user == null) throw new BizException("用户不存在");
        if (!Passwords.matches(oldPwd, user.getPassword())) throw new BizException("原密码不正确");
        user.setPassword(Passwords.hash(newPwd));
        userMapper.updateById(user);
        return R.ok("密码修改成功", null);
    }

    @GetMapping("/roles")
    public R<List<SysRole>> roles() {
        return R.ok(roleMapper.selectList(null));
    }

    private Map<String, Object> safeUser(SysUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("empNo", u.getEmpNo());
        m.put("roleCode", u.getRoleCode());
        m.put("deptName", u.getDeptName());
        m.put("phone", u.getPhone());
        return m;
    }

    private void writeLog(String username, HttpServletRequest request, boolean ok, String msg) {
        SysLoginLog log = new SysLoginLog();
        log.setUsername(username);
        log.setIp(clientIp(request));
        String ua = request.getHeader("User-Agent");
        log.setBrowser(ua == null ? "未知" : ua.substring(0, Math.min(150, ua.length())));
        log.setStatus(ok ? 1 : 0);
        log.setMsg(msg);
        log.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip == null ? "-" : ip.split(",")[0].trim();
    }

    public record LoginRequest(String username, String password) {
    }
}
