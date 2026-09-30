package com.mesmax.report;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.entity.MdMachine;
import com.mesmax.entity.ProdOrderStep;
import com.mesmax.entity.ProdStopRecord;
import com.mesmax.entity.ProdTaskReport;
import com.mesmax.mapper.MdMachineMapper;
import com.mesmax.mapper.ProdOrderStepMapper;
import com.mesmax.mapper.ProdStopRecordMapper;
import com.mesmax.mapper.ProdTaskReportMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OEE 计算的**唯一口径**。
 *
 * <p>为什么必须单独抽一个服务：单机接口 {@code /machine/{id}/oee} 与报表接口
 * {@code /stats/oee} 曾经各写一套公式，结果同一台机给出两个不同的 OEE
 * （单机那版把性能开动率硬编码成 92%）。指标口径只要有两份实现，迟早会互相打架。
 *
 * <p>口径定义（与车间实际管理一致）：
 * <pre>
 *   时间开动率 = 实际加工时间 /（实际加工时间 + 停机时间）
 *   性能开动率 = 理论节拍时间 / 实际加工时间，理论节拍 = Σ(工艺定额单件工时 × 报工数量)
 *   合格率     = 合格数量 / 报工总数
 *   OEE        = 三者相乘
 * </pre>
 *
 * <p>关于「开工」记录：{@code prod_task_report} 里的 {@code report_type='开工'} 行只是
 * 开工占位（数量与工时应为 0）。所有统计一律排除该类型，否则同一批数量会被算两次。
 */
@Service
public class OeeService {

    /** 开工占位记录的类型标识。 */
    public static final String TYPE_START = "开工";

    private final ProdTaskReportMapper reportMapper;
    private final ProdStopRecordMapper stopMapper;
    private final ProdOrderStepMapper stepMapper;
    private final MdMachineMapper machineMapper;

    public OeeService(ProdTaskReportMapper reportMapper, ProdStopRecordMapper stopMapper,
                      ProdOrderStepMapper stepMapper, MdMachineMapper machineMapper) {
        this.reportMapper = reportMapper;
        this.stopMapper = stopMapper;
        this.stepMapper = stepMapper;
        this.machineMapper = machineMapper;
    }

    /** 是否参与产量/工时统计的报工行（开工占位不参与）。 */
    public static boolean isProductionRow(ProdTaskReport r) {
        return r != null && !TYPE_START.equals(r.getReportType());
    }

    /** 一次性取出这批报工涉及到的工序任务，用于取工时定额。 */
    public Map<Long, ProdOrderStep> stepIndex(Collection<Long> stepIds) {
        Set<Long> ids = stepIds == null ? Set.of()
                : stepIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) return Map.of();
        return stepMapper.selectList(new QueryWrapper<ProdOrderStep>().in("id", ids))
                .stream().collect(Collectors.toMap(ProdOrderStep::getId, s -> s, (a, b) -> a));
    }

    /** 理论节拍时间（分钟）：工艺定额中的单件工时 × 报工数量。 */
    public static double theoryMinutes(ProdOrderStep step, double qty) {
        if (step == null || step.getPieceTime() == null) return 0;
        return step.getPieceTime().doubleValue() * qty;
    }

    /**
     * 由五项原始量算出一组 OEE 指标。所有调用方（单机 / 报表 / 按天 / 按工作中心）都走这里。
     *
     * @param theory 理论节拍时间合计；为 0 表示该范围内没有可用的工时定额，
     *               此时性能开动率与 OEE 返回 null，而不是编一个假数——
     *               指标缺失要能被看见，不能被 92% 这种"看起来合理"的数掩盖。
     */
    public Map<String, Object> metrics(double runMinutes, double stopMinutes, double qty, double defectQty, double theory) {
        double availability = runMinutes + stopMinutes <= 0 ? 0 : runMinutes / (runMinutes + stopMinutes);
        double quality = qty <= 0 ? 0 : Math.max(0, (qty - defectQty) / qty);
        Double performance = null;
        Double oee = null;
        if (theory > 0 && runMinutes > 0) {
            double p = Math.min(1.0, theory / runMinutes);
            performance = p;
            oee = availability * p * quality;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runMinutes", Math.round(runMinutes));
        m.put("stopMinutes", Math.round(stopMinutes));
        m.put("theoryMinutes", Math.round(theory));
        m.put("qty", Math.round(qty));
        m.put("defectQty", Math.round(defectQty));
        m.put("availability", round1(availability * 100));
        m.put("performance", performance == null ? null : round1(performance * 100));
        m.put("quality", round1(quality * 100));
        m.put("oee", oee == null ? null : round1(oee * 100));
        m.put("theorySource", theory > 0 ? "norms" : "missing");
        return m;
    }

    /**
     * 单机（或指定机台）在指定区间内的 OEE。
     *
     * @param machineId   机台 ID，可为 null
     * @param machineCode 机台编号，可为 null
     */
    public Map<String, Object> compute(LocalDate from, LocalDate to, Long machineId, String machineCode) {
        QueryWrapper<ProdTaskReport> rq = new QueryWrapper<ProdTaskReport>().ge("work_date", from).le("work_date", to);
        if (machineId != null) rq.eq("machine_id", machineId);
        if (machineCode != null) rq.eq("machine_code", machineCode);
        List<ProdTaskReport> all = reportMapper.selectList(rq);
        List<ProdTaskReport> reports = all.stream().filter(OeeService::isProductionRow).toList();

        /*
         * 停机记录必须同时卡上下界。原来只写了 ge(start_time, from) —— 少了下界那一半，
         * 只要区间右端不是「今天」（例如以后加「查上个月」），下界之后的所有停机都会被算进来，
         * 时间开动率被系统性拉低，而报表上完全看不出来。
         */
        QueryWrapper<ProdStopRecord> sq = new QueryWrapper<ProdStopRecord>()
                .ge("start_time", from.atStartOfDay())
                .lt("start_time", to.plusDays(1).atStartOfDay());
        if (machineId != null) sq.eq("machine_id", machineId);
        if (machineCode != null) sq.eq("machine_code", machineCode);
        List<ProdStopRecord> stops = stopMapper.selectList(sq);

        Map<Long, ProdOrderStep> stepMap = stepIndex(reports.stream().map(ProdTaskReport::getStepId).toList());

        double run = 0;
        double qty = 0;
        double defect = 0;
        double theory = 0;
        for (ProdTaskReport r : reports) {
            double q = nz(r.getQty());
            run += nz(r.getRunMinutes());
            qty += q;
            defect += nz(r.getDefectQty());
            theory += theoryMinutes(stepMap.get(r.getStepId()), q);
        }
        double stop = stops.stream().mapToDouble(s -> nz(s.getDurationMin())).sum();

        Map<String, Object> m = metrics(run, stop, qty, defect, theory);
        m.put("machineId", machineId);
        m.put("machineCode", machineCode);
        m.put("from", from.toString());
        m.put("to", to.toString());
        if (machineId != null) {
            MdMachine mac = machineMapper.selectById(machineId);
            if (mac != null) {
                double target = mac.getOeeTarget() == null ? 80 : mac.getOeeTarget().doubleValue();
                // 把机台标识补齐，前端与报表接口才能按同一把钥匙（机台编号）对上
                m.put("machineCode", machineCode == null ? mac.getMacCode() : machineCode);
                m.put("machineName", mac.getMacName());
                m.put("cncType", mac.getCncType());
                m.put("workCenterId", mac.getWorkCenterId());
                m.put("target", target);
                Object oee = m.get("oee");
                m.put("gap", oee == null ? null : Math.round(((double) oee - target) * 10) / 10.0);
            }
        }
        return m;
    }

    private static double nz(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** 供报表复用：按天/按工作中心聚合时的区间起点（含当日）。 */
    public static LocalDateTime dayStart(LocalDate d) {
        return d.atStartOfDay();
    }
}
