package com.mesmax.common;

import com.mesmax.entity.SysOperLog;
import com.mesmax.mapper.SysOperLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 登录与权限拦截。
 *
 * <p>令牌来源优先级：{@code Authorization: Bearer xxx} → 请求头 {@code X-Token} → 查询参数 {@code token}
 * （最后一种只为 SSE 实时看板保留：浏览器 EventSource 无法自定义请求头。
 * <b>查询串里的令牌会进入 Nginx access log 等外部日志，因此只对 {@code /board/stream} 开放</b>，
 * 其余接口一律要求走请求头）。
 *
 * <p>安全要点（都是踩过的坑，写在这里备忘）：
 * <ol>
 *   <li>路径判定必须用**解码后**的查找路径，否则 {@code /api/%73ystem/...} 可绕过模块权限；</li>
 *   <li>角色权限查不到时必须**拒绝**，不能默认放行 —— 否则登录时把角色改成不存在的编码即可提权；</li>
 *   <li>401/403 要落到真实 HTTP 状态码，网关、Nginx、axios 拦截器才认得出来；</li>
 *   <li>操作日志里的查询串要做令牌脱敏。</li>
 *   <li>SSE 是异步请求：登录上下文必须在「请求转异步的那一刻」清掉，否则处理线程回到线程池时
 *       还挂着上一个用户 —— 这也是本类实现 {@link AsyncHandlerInterceptor} 的原因。</li>
 * </ol>
 */
@Component
public class AuthInterceptor implements AsyncHandlerInterceptor {

    /** 唯一允许用查询参数携带令牌的接口（EventSource 无法自定义请求头）。 */
    private static final String SSE_TOKEN_PATH = "/board/stream";

    /** 关闭 alwaysUseFullPath 语义：取「应用内查找路径」（已去 context-path），并做 URL 解码。 */
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    static {
        PATH_HELPER.setUrlDecode(true);
        PATH_HELPER.setRemoveSemicolonContent(true);
    }

    private final SysOperLogMapper operLogMapper;

    public AuthInterceptor(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        request.setAttribute("operStart", System.currentTimeMillis());

        String token = resolveToken(request);
        Map<String, Object> claims = JwtUtil.verify(token);
        if (claims == null) {
            write(response, 401, "登录状态已失效，请重新登录");
            return false;
        }
        // 权限校验通过之后才把登录上下文放进 ThreadLocal：
        // 若在 403 分支之前 set，由于 preHandle 返回 false 时 Spring 不会回调
        // afterCompletion，这个 ThreadLocal 就会留在处理线程上（线程池复用 → 泄漏）。
        String module = ModuleOf.requiredPermission(request.getMethod(), lookupPath(request));
        if (module != null && !hasModule(claims, module)) {
            write(response, 403, "当前账号无该功能权限");
            return false;
        }
        UserContext.set(claims);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            recordOperLog(request, ex);
        } catch (Exception e) {
            // 日志写入失败不能影响业务请求
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 请求转入异步（SSE 订阅）时调用。
     *
     * <p>此刻容器线程即将被回收去处理别的请求，而 {@code afterCompletion} 要等到异步完成
     *（SSE 连接关闭，可能是几小时后）才执行。不在这里清理，ThreadLocal 就会被上一个
     * 用户的身份占用整个连接生命周期。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
    }

    /** 解码后的应用内查找路径（已去掉 context-path）。 */
    private String lookupPath(HttpServletRequest request) {
        try {
            String p = PATH_HELPER.getLookupPathForRequest(request);
            return p == null ? "" : p;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 角色是否具备某模块权限。
     *
     * <p>未知角色（查不到权限配置）一律返回 false —— 登录接口不校验角色是否存在，
     * 若此处默认放行，只要把账号的 role_code 改成任意字符串，写接口就全开了。
     */
    private boolean hasModule(Map<String, Object> claims, String module) {
        String role = JwtUtil.str(claims, "role");
        if ("admin".equals(role)) return true;
        String perms = PermRegistry.perms(role);
        if (perms == null) return false;
        if (perms.contains("*")) return true;
        for (String p : perms.split(",")) {
            if (p.trim().equalsIgnoreCase(module)) return true;
        }
        return false;
    }

    /** 写操作留痕：写库失败不影响业务，因此整体 try-catch。 */
    private void recordOperLog(HttpServletRequest request, Exception ex) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) return;
        String username = UserContext.username();
        if (username == null) return;

        String uri = request.getRequestURI();
        SysOperLog log = new SysOperLog();
        log.setModule(ModuleOf.moduleName(lookupPath(request)));
        log.setAction(method);
        log.setUri(uri.length() > 190 ? uri.substring(0, 190) : uri);
        log.setMethod(method);
        log.setParams(maskSecrets(request.getQueryString()));
        log.setUsername(username);
        log.setIp(clientIp(request));
        Object start = request.getAttribute("operStart");
        log.setCostMs(start instanceof Long l ? System.currentTimeMillis() - l : 0L);
        log.setStatus(ex == null ? 1 : 0);
        log.setMsg(ex == null ? "成功" : ex.getMessage());
        log.setOperTime(LocalDateTime.now());
        operLogMapper.insert(log);
    }

    /** 查询串中的令牌必须脱敏后再落库，避免操作日志变成令牌泄露渠道。 */
    private String maskSecrets(String qs) {
        if (qs == null || qs.isEmpty()) return "";
        String masked = qs.replaceAll("(?i)(token|password|pwd|secret)=[^&]*", "$1=***");
        return masked.length() > 1900 ? masked.substring(0, 1900) : masked;
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip == null ? "-" : ip.split(",")[0].trim();
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7).trim();
        String header = request.getHeader("X-Token");
        if (header != null && !header.isBlank()) return header.trim();
        // 只为 SSE 放行查询串令牌（见类注释）
        String path = lookupPath(request);
        if (path != null && path.startsWith(SSE_TOKEN_PATH)) {
            return request.getParameter("token");
        }
        return null;
    }

    /** 返回真实 HTTP 状态码，同时在响应体里保留统一的业务码（前端两种都能处理）。 */
    private void write(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg
                + "\",\"data\":null,\"ts\":" + System.currentTimeMillis() + "}");
    }
}
