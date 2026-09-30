package com.mesmax;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MES-MAX 启动类。
 *
 * <p>面向 CNC 数控加工行业的制造执行系统。启动时自动完成：
 * <ol>
 *   <li>建表（scripts/schema.sql，幂等）</li>
 *   <li>灌入车间主数据（scripts/seed-data.sql，仅空库时执行）</li>
 *   <li>生成 14 天确定性业务流水（订单/报工/停机/遥测/检验/追溯）</li>
 * </ol>
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.mesmax.mapper")
public class MesMaxApplication {
    public static void main(String[] args) {
        SpringApplication.run(MesMaxApplication.class, args);
    }
}
