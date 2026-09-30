package com.mesmax.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * 全局异常处理。
 *
 * <p>两条规矩：
 * <ol>
 *   <li><b>HTTP 状态码与业务码保持一致</b>。早先这里一律返回 HTTP 200（响应体里写 code=401/500），
 *       而拦截器那边又返回真实 401/403 —— 同一个后端两套语义，Nginx、网关、前端 fetch
 *       各有各的判断依据。现在 4xx 业务拒绝就是 4xx，只有真正的服务端故障才是 500。</li>
 *   <li><b>不把内部异常信息回吐给客户端</b>。原来 {@code "服务端异常：" + e.getMessage()}
 *       会把 SQL 片段、类名、字段名一起送到车间大屏上；现在只给追踪号，细节进服务端日志。</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：业务码即 HTTP 状态码。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(httpStatusOf(e.getCode()))
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 参数类异常：请求里的数字/时间没解析出来、请求体不是合法 JSON、缺必填参数……
     * 这些都是「客户端把请求写错了」，必须落到 400，而不是当成服务端故障。
     */
    @ExceptionHandler({
            IllegalArgumentException.class,
            DateTimeParseException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
    })
    public ResponseEntity<R<Void>> handleBadRequest(Exception e) {
        /*
         * 逐类给「人话」提示，不直接透传框架异常消息：
         * HttpMessageNotReadableException 的默认文案形如
         * 「Required request body is missing: public com.mesmax.common.R<T> com.mesmax.common.BaseCrudController.save(T)」，
         * 把内部类名与方法签名一并泄给了前端。
         */
        String msg;
        if (e instanceof DateTimeParseException) {
            msg = "时间格式不正确，应形如 2026-09-30 08:30:00 或 2026-09-30T08:30:00";
        } else if (e instanceof HttpMessageNotReadableException) {
            msg = "请求体缺失或不是合法的 JSON";
        } else if (e instanceof MissingServletRequestParameterException m) {
            msg = "缺少必填参数：" + m.getParameterName();
        } else if (e instanceof MethodArgumentTypeMismatchException t) {
            msg = "参数 " + t.getName() + " 类型不正确，实际收到：" + t.getValue();
        } else if (e.getMessage() == null || e.getMessage().isBlank()) {
            msg = "请求参数不合法";
        } else {
            msg = e.getMessage();
        }
        log.debug("请求参数不合法：{}", e.getMessage());
        return ResponseEntity.badRequest().body(R.fail(400, msg));
    }

    /** 方法不支持：落到 405，而不是 500。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(R.fail(405, "该接口不支持 " + e.getMethod() + " 方法"));
    }

    /**
     * 路径本身不存在：落到 404。
     *
     * <p>未映射的 URL 会被 Spring 交给静态资源处理器，最终抛 {@code NoResourceFoundException}；
     * 不单独处理的话它会掉进下面的兜底分支变成「500 服务端异常」—— 把「你把地址写错了」
     * 报成「后端挂了」，排查方向直接跑偏（这次就是靠一个探针请求才发现的）。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<R<Void>> handleNoHandler(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(404, "接口不存在"));
    }

    /**
     * 兜底：真正的服务端异常。
     *
     * <p>只回吐一个追踪号，堆栈只进服务端日志 —— 排查时用追踪号去日志里搜，
     * 既不丢信息，也不把内部结构泄给车间终端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("接口异常 traceId={}", traceId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, "服务端异常，请联系管理员（追踪号 " + traceId + "）"));
    }

    private static HttpStatus httpStatusOf(int code) {
        return switch (code) {
            case BizException.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case BizException.FORBIDDEN -> HttpStatus.FORBIDDEN;
            case BizException.NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BizException.CONFLICT -> HttpStatus.CONFLICT;
            case BizException.TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
