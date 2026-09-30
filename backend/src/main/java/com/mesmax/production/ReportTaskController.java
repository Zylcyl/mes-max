package com.mesmax.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mesmax.common.BizException;
import com.mesmax.common.PageResult;
import com.mesmax.common.R;
import com.mesmax.entity.ProdTaskReport;
import com.mesmax.mapper.ProdTaskReportMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 报工明细查询（只读）。
 *
 * <p>为什么单独开一个只读控制器：{@code ModuleOf} 与验证脚本里一直有 {@code /report-task} 这个模块，
 * 但后端根本没有对应接口 —— 结果是「开工占位记录数量必须为 0」这类断言在脚本里**空跑也通过**
 * （拿不到数据 → 过滤后数组为空 → 断言成立），谁都没发现。补齐读接口后，这类断言才真正有效：
 * 车间要能查某道工序今天报了多少次、计划员要能核对某张工票的报工流水。
 *
 * <p>刻意**不提供**新增/修改/删除：报工记录是 OEE、产量、追溯的事实来源，
 * 只能由车间开工/报工动作产生（见 {@link ProductionService}），不允许从接口直接改。
 */
@RestController
@RequestMapping("/report-task")
public class ReportTaskController {

    private static final long MAX_PAGE_SIZE = 500;
    private static final long DEFAULT_PAGE_SIZE = 20;

    private final ProdTaskReportMapper mapper;

    public ReportTaskController(ProdTaskReportMapper mapper) {
        this.mapper = mapper;
    }

    /** 分页查询：可按工序任务 / 工单 / 机台 / 日期 / 报工类型过滤。 */
    @GetMapping("/page")
    public R<PageResult<ProdTaskReport>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) Long stepId,
                                              @RequestParam(required = false) Long orderId,
                                              @RequestParam(required = false) Long machineId,
                                              @RequestParam(required = false) Long dispatchId,
                                              @RequestParam(required = false) String workDate,
                                              @RequestParam(required = false) String reportType) {
        QueryWrapper<ProdTaskReport> qw = new QueryWrapper<>();
        if (stepId != null) qw.eq("step_id", stepId);
        if (orderId != null) qw.eq("order_id", orderId);
        if (machineId != null) qw.eq("machine_id", machineId);
        if (dispatchId != null) qw.eq("dispatch_id", dispatchId);
        if (reportType != null && !reportType.isBlank()) qw.eq("report_type", reportType);
        if (workDate != null && !workDate.isBlank()) {
            // 支持 2026-09-30 / 2026-09-30 08:00:00 两种写法：只取日期部分与 work_date 比较
            qw.eq("work_date", LocalDate.parse(workDate.trim().split("[ T]")[0]));
        }
        qw.orderByDesc("report_time").orderByDesc("id");

        long p = Math.max(1, Math.min(page, 100000));
        long s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        var result = mapper.selectPage(new Page<>(p, s), qw);
        return R.ok(PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords()));
    }

    /** 简化列表（最多 500 条），用于工票打印 / 工序报工流水。 */
    @GetMapping("/list")
    public R<List<ProdTaskReport>> list(@RequestParam(required = false) Long stepId,
                                        @RequestParam(required = false) Long orderId,
                                        @RequestParam(required = false) Long machineId) {
        QueryWrapper<ProdTaskReport> qw = new QueryWrapper<>();
        if (stepId != null) qw.eq("step_id", stepId);
        if (orderId != null) qw.eq("order_id", orderId);
        if (machineId != null) qw.eq("machine_id", machineId);
        qw.orderByDesc("report_time").orderByDesc("id").last("limit " + MAX_PAGE_SIZE);
        return R.ok(mapper.selectList(qw));
    }

    @GetMapping("/{id}")
    public R<ProdTaskReport> get(@PathVariable Long id) {
        ProdTaskReport r = mapper.selectById(id);
        if (r == null) throw new BizException(BizException.NOT_FOUND, "报工记录不存在：" + id);
        return R.ok(r);
    }
}
