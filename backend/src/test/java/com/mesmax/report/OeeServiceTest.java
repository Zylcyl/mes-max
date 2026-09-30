package com.mesmax.report;

import com.mesmax.entity.ProdOrderStep;
import com.mesmax.entity.ProdTaskReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OEE 口径的单元测试。
 * 只测纯计算，不依赖数据库：metrics() 不读任何字段，因此可以 new 一个"空壳"实例调用。
 */
class OeeServiceTest {

    private final OeeService service = new OeeService(null, null, null, null);

    @Test
    @DisplayName("三项相乘：100 分钟加工 / 100 分钟停机 / 200 件 1 不良 / 理论 90 分钟")
    void metricsMath() {
        Map<String, Object> m = service.metrics(100, 100, 200, 1, 90);
        assertEquals(50.0, (double) m.get("availability"), 0.01, "时间开动率 = 100/(100+100)");
        assertEquals(90.0, (double) m.get("performance"), 0.01, "性能开动率 = 90/100");
        assertEquals(99.5, (double) m.get("quality"), 0.01, "合格率 = 199/200");
        assertEquals(50 * 0.9 * 0.995, (double) m.get("oee"), 0.05);
        assertEquals("norms", m.get("theorySource"));
    }

    @Test
    @DisplayName("拿不到工时定额时不编数：performance 与 oee 返回 null")
    void missingNormsYieldsNull() {
        Map<String, Object> m = service.metrics(100, 0, 50, 0, 0);
        assertNull(m.get("performance"));
        assertNull(m.get("oee"));
        assertEquals("missing", m.get("theorySource"));
        assertEquals(100.0, (double) m.get("quality"), 0.01);
    }

    @Test
    @DisplayName("性能开动率封顶 100%（定额高于实际用时说明定额本身有问题）")
    void performanceCapped() {
        Map<String, Object> m = service.metrics(100, 0, 10, 0, 500);
        assertEquals(100.0, (double) m.get("performance"), 0.01);
    }

    @Test
    @DisplayName("无数据时不出现除零：开动率与合格率为 0")
    void zeroInputsSafe() {
        Map<String, Object> m = service.metrics(0, 0, 0, 0, 0);
        assertEquals(0.0, (double) m.get("availability"), 0.01);
        assertEquals(0.0, (double) m.get("quality"), 0.01);
        assertNull(m.get("oee"));
    }

    @Test
    @DisplayName("开工占位记录不参与产量统计")
    void startRowIsNotProductionRow() {
        ProdTaskReport start = new ProdTaskReport();
        start.setReportType("开工");
        ProdTaskReport report = new ProdTaskReport();
        report.setReportType("报工");
        ProdTaskReport finish = new ProdTaskReport();
        finish.setReportType("完工");

        assertFalse(OeeService.isProductionRow(start));
        assertTrue(OeeService.isProductionRow(report));
        assertTrue(OeeService.isProductionRow(finish));
        assertFalse(OeeService.isProductionRow(null));
    }

    @Test
    @DisplayName("理论节拍 = 单件工时定额 × 报工数量；定额缺失则为 0")
    void theoryMinutes() {
        ProdOrderStep step = new ProdOrderStep();
        step.setPieceTime(new BigDecimal("2.50"));
        assertEquals(25.0, OeeService.theoryMinutes(step, 10), 0.001);

        ProdOrderStep noNorm = new ProdOrderStep();
        assertEquals(0.0, OeeService.theoryMinutes(noNorm, 10), 0.001);
        assertEquals(0.0, OeeService.theoryMinutes(null, 10), 0.001);
    }
}
