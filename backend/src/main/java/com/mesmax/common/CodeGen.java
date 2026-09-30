package com.mesmax.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务单号生成器。
 *
 * <p>格式：{@code 前缀 + yyyyMMdd + 4位序号}，例如 {@code WO202609210001}（工单）。
 * 序号在进程内自增 + 随机初值，保证多实例部署时不冲突的概率足够低；
 * 生产环境可替换为数据库序列表或 Redis 自增。
 */
public final class CodeGen {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 9000 + 1000);

    private CodeGen() {
    }

    public static String next(String prefix) {
        return prefix + LocalDate.now().format(DAY) + String.format("%04d", SEQ.incrementAndGet() % 10000);
    }
}
