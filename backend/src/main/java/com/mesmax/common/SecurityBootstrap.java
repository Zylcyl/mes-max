package com.mesmax.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动即完成鉴权相关初始化，让「令牌密钥从哪来」在启动日志第一屏就能看到。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityBootstrap.class);

    @Override
    public void run(ApplicationArguments args) {
        JwtUtil.ensureInitialized();
        log.info("鉴权初始化完成：令牌有效期 12 小时，密钥策略 = 环境变量 MES_JWT_SECRET → 本机 .jwt-secret → 随机生成并落盘");
    }
}
