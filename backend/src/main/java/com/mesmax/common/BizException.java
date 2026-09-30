package com.mesmax.common;

/**
 * 业务异常：由 GlobalExceptionHandler 转成统一的 {@link R} 响应返回前端。
 *
 * <p>默认业务码是 <b>400</b> 而不是 500：这类异常全部是「客户端可纠正」的拒绝
 * （数量为负、状态不允许、缺少参数、记录不存在……），把它当服务端故障上报会污染
 * 监控与网关的错误率，也会让调用方误以为是后端挂了。真正的服务端异常走
 * {@code GlobalExceptionHandler#handleOther}，那才是 500。
 */
public class BizException extends RuntimeException {
    /** 缺少/非法参数。 */
    public static final int BAD_REQUEST = 400;
    /** 未登录 / 登录失效。 */
    public static final int UNAUTHORIZED = 401;
    /** 已登录但无权限。 */
    public static final int FORBIDDEN = 403;
    /** 记录不存在。 */
    public static final int NOT_FOUND = 404;
    /** 状态冲突（例如对已完工的派工单再次开工）。 */
    public static final int CONFLICT = 409;
    /** 触发节流（登录失败次数过多）。 */
    public static final int TOO_MANY_REQUESTS = 429;

    private final int code;

    public BizException(String message) {
        this(BAD_REQUEST, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
