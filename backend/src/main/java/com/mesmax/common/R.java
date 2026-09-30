package com.mesmax.common;

import lombok.Data;

/** 统一响应体。车间终端与服务端约定：code=200 成功，401 未登录，403 无权限，500 业务异常。 */
@Data
public class R<T> {
    private int code;
    private String msg;
    private T data;
    private long ts = System.currentTimeMillis();

    public static <T> R<T> ok() { return ok(null); }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok(String msg, T data) {
        R<T> r = ok(data);
        r.msg = msg;
        return r;
    }

    public static <T> R<T> fail(String msg) { return fail(500, msg); }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
