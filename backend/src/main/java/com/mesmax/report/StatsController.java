package com.mesmax.report;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.common.R;
import com.mesmax.entity.MdEmployee;
import com.mesmax.entity.MdMachine;
import com.mesmax.entity.MdWorkCenter;
import com.mesmax.entity.MdWorkshop;
import com.mesmax.entity.ProdOrder;
import com.mesmax.entity.ProdOrderStep;
import com.mesmax.entity.ProdStopRecord;
import com.mesmax.entity.ProdTaskReport;
import com.mesmax.mapper.MdEmployeeMapper;
import com.mesmax.mapper.MdMachineMapper;
import com.mesmax.mapper.MdWorkCenterMapper;
import com.mesmax.mapper.MdWorkshopMapper;
import com.mesmax.mapper.ProdOrderMapper;
import com.mesmax.mapper.ProdOrderStepMapper;
import com.mesmax.mapper.ProdStopRecordMapper;
import com.mesmax.mapper.ProdTaskReportMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 统计报表：OEE / 产量 / 质量 / 工时 / 设备利用率。
 *
 * <p>OEE 口径（与车间实际管理口径一致，而不是教科书上的理想口径）：
 * <ul>
 *   <li><b>时间开动率</b> = 实际加工时间 /（实际加工时间 + 停机时间）</li>
 *   <li><b>性能开动率</b> = 理论节拍时间 / 实际加工时间。理论节拍取工艺路线的单件工时
 *       （即 industry4.0-mes 中的 time-norms，来自工艺定额而不是客服拍脑袋）</li>
 *   <li><b>合格率</b> = 合格数量 / 报工总数</li>
 * </ul>
 */
@RestController
@RequestMapping("/stats")
public class StatsController {

    private final ProdTaskReportMapper reportMapper;
    private final ProdStopRecordMapper stopMapper;
    private final ProdOrderStepMapper stepMapper;
    private final ProdOrderMapper orderMapper;
    private final MdMachineMapper machineMapper;
    private final MdWorkCenterMapper workCenterMapper;
    private final MdWorkshopMapper workshopMapper;
    private final MdEmployeeMapper employeeMapper;
    private final OeeService oeeService;

    public StatsController(ProdTaskReportMapper reportMapper, ProdStopRecordMapper stopMapper,
                           ProdOrderStepMapper stepMapper, ProdOrderMapper orderMapper,
                           MdMachineMapper machineMapper, MdWorkCenterMapper workCenterMapper,
                           MdWorkshopMapper workshopMapper, MdEmployeeMapper employeeMapper,
                           OeeService oeeService) {
        this.reportMapper = reportMapper;
        this.stopMapper = stopMapper;
        this.stepMapper = stepMapper;
        this.orderMapper = orderMapper;
        this.machineMapper = machineMapper;
        this.workCenterMapper = workCenterMapper;
        this.workshopMapper = workshopMapper;
        this.employeeMapper = employeeMapper;
        this.oeeService = oeeService;
    }

    /** OEE 总览：整体 + 按机台 + 按天 + 按工作中心。 */
    @GetMapping("/oee")
    public R<Map<String, Object>> oee(@RequestParam(defaultValue = "7") int days) {
        int span = clampDays(days);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(span - 1L);
        List<ProdTaskReport> reports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .ge("work_date", from).le("work_date", to).orderByAsc("work_date"));
        // 上下界都要写：只有下界时，区间右端之后的数据会被静默计入（见 OeeService 的同类注释）
        List<ProdStopRecord> stops = stopMapper.selectList(new QueryWrapper<ProdStopRecord>()
                .ge("start_time", from.atStartOfDay())
                .lt("start_time", to.plusDays(1).atStartOfDay()));
        Map<Long, ProdOrderStep> stepMap = stepIndex();

        Map<String, double[]> byMachine = new TreeMap<>();
        Map<String, double[]> byDay = new TreeMap<>();
        double runAll = 0;
        double stopAll = 0;
        double qtyAll = 0;
        double defAll = 0;
        double theoryAll = 0;

        for (ProdTaskReport r : reports) {
            if ("开工".equals(r.getReportType())) continue;
            double run = nz(r.getRunMinutes());
            double qty = nz(r.getQty());
            double def = nz(r.getDefectQty());
            ProdOrderStep st = r.getStepId() == null ? null : stepMap.get(r.getStepId());
            // 理论节拍只能来自工艺定额。定额缺失时宁可在指标上留空，也不编一个「看起来合理」的数
            double theory = OeeService.theoryMinutes(st, qty);
            runAll += run;
            qtyAll += qty;
            defAll += def;
            theoryAll += theory;
            // 去毛刺、清洗、三坐标检测等手工工位没有机床，单独归为一类，避免它们把机器 OEE 拉偏
            String mk = r.getMachineCode() == null ? "手工/检测工位" : r.getMachineCode();
            byMachine.computeIfAbsent(mk, k -> new double[6]);
            double[] mv = byMachine.get(mk);
            mv[0] += run;
            mv[1] += qty;
            mv[2] += def;
            mv[3] += theory;
            if (r.getWorkDate() != null) {
                byDay.computeIfAbsent(r.getWorkDate().toString(), k -> new double[6]);
                double[] dv = byDay.get(r.getWorkDate().toString());
                dv[0] += run;
                dv[1] += qty;
                dv[2] += def;
                dv[3] += theory;
            }
        }
        for (ProdStopRecord s : stops) {
            stopAll += nz(s.getDurationMin());
            if (s.getMachineCode() != null) {
                byMachine.computeIfAbsent(s.getMachineCode(), k -> new double[6]);
                byMachine.get(s.getMachineCode())[4] += nz(s.getDurationMin());
            }
            if (s.getStartTime() != null) {
                String d = s.getStartTime().toLocalDate().toString();
                byDay.computeIfAbsent(d, k -> new double[6]);
                byDay.get(d)[4] += nz(s.getDurationMin());
            }
        }

        List<Map<String, Object>> machineList = new ArrayList<>();
        for (Map.Entry<String, double[]> e : byMachine.entrySet()) {
            double[] v = e.getValue();
            Map<String, Object> m = oeeOf(e.getKey(), v[0], v[4], v[1], v[2], v[3]);
            MdMachine mac = machineMapper.selectOne(new QueryWrapper<MdMachine>()
                    .eq("mac_code", e.getKey()).last("limit 1"));
            m.put("machineName", mac == null ? null : mac.getMacName());
            m.put("cncType", mac == null ? null : mac.getCncType());
            m.put("target", mac == null || mac.getOeeTarget() == null ? 80 : mac.getOeeTarget().doubleValue());
            m.put("workCenterId", mac == null ? null : mac.getWorkCenterId());
            machineList.add(m);
        }
        machineList.sort((a, b) -> Double.compare(oeeValue(b), oeeValue(a)));

        List<Map<String, Object>> dayList = new ArrayList<>();
        byDay.forEach((d, v) -> {
            Map<String, Object> m = oeeOf(d, v[0], v[4], v[1], v[2], v[3]);
            m.put("key", "day");
            m.put("label", d);
            m.put("defectQty", Math.round(v[2]));
            dayList.add(m);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overall", oeeOf("总体", runAll, stopAll, qtyAll, defAll, theoryAll));
        result.put("byMachine", machineList);
        result.put("byDay", dayList);
        result.put("byWorkCenter", byWorkCenter(reports, stops, stepMap));
        result.put("days", span);
        return R.ok(result);
    }

    private List<Map<String, Object>> byWorkCenter(List<ProdTaskReport> reports, List<ProdStopRecord> stops,
                                                   Map<Long, ProdOrderStep> stepMap) {
        Map<Long, double[]> acc = new TreeMap<>();
        for (ProdTaskReport r : reports) {
            if ("开工".equals(r.getReportType())) continue;
            ProdOrderStep st = r.getStepId() == null ? null : stepMap.get(r.getStepId());
            Long wcId = st == null ? null : st.getWcId();
            if (wcId == null) continue;
            double qty = nz(r.getQty());
            double theory = OeeService.theoryMinutes(st, qty);
            acc.computeIfAbsent(wcId, k -> new double[6]);
            double[] v = acc.get(wcId);
            v[0] += nz(r.getRunMinutes());
            v[1] += qty;
            v[2] += nz(r.getDefectQty());
            v[3] += theory;
        }
        for (ProdStopRecord s : stops) {
            ProdOrderStep st = s.getStepId() == null ? null : stepMap.get(s.getStepId());
            Long wcId = st == null ? null : st.getWcId();
            if (wcId == null) continue;
            acc.computeIfAbsent(wcId, k -> new double[6]);
            acc.get(wcId)[4] += nz(s.getDurationMin());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        acc.forEach((wcId, v) -> {
            MdWorkCenter wc = workCenterMapper.selectById(wcId);
            Map<String, Object> m = oeeOf(wc == null ? String.valueOf(wcId) : wc.getWcName(), v[0], v[4], v[1], v[2], v[3]);
            m.put("workCenterId", wcId);
            m.put("wcCode", wc == null ? null : wc.getWcCode());
            list.add(m);
        });
        list.sort((a, b) -> Double.compare(oeeValue(b), oeeValue(a)));
        return list;
    }

    /** 排序用的 OEE 取值：null（缺定额）视为 0，避免排序列里 NPE。 */
    private static double oeeValue(Map<String, Object> row) {
        Object v = row.get("oee");
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    /** 统一的 OEE 口径：委托给 OeeService，保证与单机接口数字一致。 */
    private Map<String, Object> oeeOf(String label, double run, double stop, double qty, double defect, double theory) {
        Map<String, Object> m = new LinkedHashMap<>(oeeService.metrics(run, stop, qty, defect, theory));
        m.put("label", label);
        return m;
    }

    /** 产量与交期达成。 */
    @GetMapping("/output")
    public R<Map<String, Object>> output(@RequestParam(defaultValue = "14") int days) {
        int span = clampDays(days);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(span - 1L);
        List<ProdTaskReport> reports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .ge("work_date", from).le("work_date", to));
        Map<String, double[]> byDay = new TreeMap<>();
        for (int i = 0; i < span; i++) byDay.put(from.plusDays(i).toString(), new double[3]);
        for (ProdTaskReport r : reports) {
            if (r.getWorkDate() == null || "开工".equals(r.getReportType())) continue;
            double[] v = byDay.get(r.getWorkDate().toString());
            if (v == null) continue;
            v[0] += nz(r.getQty());
            v[1] += nz(r.getDefectQty());
            v[2] += nz(r.getQualifiedQty());
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        byDay.forEach((d, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", d);
            m.put("qty", Math.round(v[0]));
            m.put("defect", Math.round(v[1]));
            m.put("qualified", Math.round(v[2]));
            m.put("rate", v[0] == 0 ? null : Math.round(v[2] / v[0] * 1000) / 10.0);
            daily.add(m);
        });

        List<ProdOrder> orders = orderMapper.selectList(new QueryWrapper<ProdOrder>()
                .in("status", List.of("IN_PROGRESS", "FINISHED", "RELEASED", "SCHEDULED", "HOLD")));
        long finished = orders.stream().filter(o -> "FINISHED".equals(o.getStatus())).count();
        long tardy = orders.stream().filter(o -> o.getPlanEnd() != null
                && o.getPlanEnd().isBefore(LocalDateTime.now()) && !"FINISHED".equals(o.getStatus())).count();
        double planQty = orders.stream().mapToDouble(o -> nz(o.getPlanQty())).sum();
        double finishedQty = orders.stream().mapToDouble(o -> nz(o.getFinishedQty())).sum();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("daily", daily);
        res.put("orderCount", orders.size());
        res.put("finishedOrders", finished);
        res.put("tardyOrders", tardy);
        res.put("onTimeRate", orders.isEmpty() ? 0 : Math.round((orders.size() - tardy) * 1000.0 / orders.size()) / 10.0);
        res.put("planQty", Math.round(planQty));
        res.put("finishedQty", Math.round(finishedQty));
        res.put("completionRate", planQty == 0 ? 0 : Math.round(finishedQty / planQty * 1000) / 10.0);
        return R.ok(res);
    }

    /** 工时与人员产出。 */
    @GetMapping("/labor")
    public R<Map<String, Object>> labor(@RequestParam(defaultValue = "7") int days) {
        int span = clampDays(days);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(span - 1L);
        List<ProdTaskReport> reports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .ge("work_date", from).le("work_date", to));
        Map<String, double[]> byOperator = new TreeMap<>();
        Map<String, Double> byShift = new TreeMap<>();
        for (ProdTaskReport r : reports) {
            if ("开工".equals(r.getReportType())) continue;
            String op = r.getEmployeeName() == null ? "未指派" : r.getEmployeeName();
            byOperator.computeIfAbsent(op, k -> new double[3]);
            double[] v = byOperator.get(op);
            v[0] += nz(r.getRunMinutes());
            v[1] += nz(r.getQty());
            v[2] += nz(r.getDefectQty());
            byShift.merge(r.getShiftName() == null ? "未知班次" : r.getShiftName(), nz(r.getRunMinutes()), Double::sum);
        }
        List<Map<String, Object>> operators = new ArrayList<>();
        byOperator.forEach((name, v) -> {
            MdEmployee e = employeeMapper.selectOne(new QueryWrapper<MdEmployee>()
                    .eq("emp_name", name).last("limit 1"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("operator", name);
            m.put("runMinutes", Math.round(v[0]));
            m.put("hours", Math.round(v[0] / 6.0) / 10.0);
            m.put("qty", Math.round(v[1]));
            m.put("defect", Math.round(v[2]));
            m.put("rate", v[1] == 0 ? null : Math.round((v[1] - v[2]) / v[1] * 1000) / 10.0);
            m.put("efficiency", Math.round(v[1] / Math.max(1, v[0] / 60.0) * 10) / 10.0);
            m.put("skillLevel", e == null ? null : e.getSkillLevel());
            m.put("teamName", e == null ? null : e.getTeamName());
            operators.add(m);
        });
        operators.sort((a, b) -> Double.compare(((Number) b.get("qty")).doubleValue(),
                ((Number) a.get("qty")).doubleValue()));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("operators", operators);
        res.put("byShift", byShift);
        res.put("totalRunMinutes", Math.round(byOperator.values().stream().mapToDouble(v -> v[0]).sum()));
        res.put("totalQty", Math.round(byOperator.values().stream().mapToDouble(v -> v[1]).sum()));
        return R.ok(res);
    }

    /** 报表首页所需的全部聚合数据（一次请求拿全，避免看板打开时并发 6 个请求）。 */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("oee", oee(days).getData());
        res.put("output", output(14).getData());
        res.put("labor", labor(7).getData());
        res.put("workshops", workshopMapper.selectList(new QueryWrapper<MdWorkshop>().orderByAsc("id")));
        res.put("machineCount", machineMapper.selectList(null).size());
        return R.ok(res);
    }

    private Map<Long, ProdOrderStep> stepIndex() {
        Map<Long, ProdOrderStep> map = new LinkedHashMap<>();
        for (ProdOrderStep s : stepMapper.selectList(new QueryWrapper<ProdOrderStep>().last("limit 20000"))) {
            map.put(s.getId(), s);
        }
        return map;
    }

    /**
     * 统计窗口天数兜底：{@code days=0} 会让 from = 今天+1（未来），{@code days=-5} 更离谱，
     * 结果是一份「什么都是 0」的报表却没有任何提示；{@code days=100000} 则等于全表扫描。
     */
    private static int clampDays(int days) {
        return Math.max(1, Math.min(days, 365));
    }

    private static double nz(java.math.BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }
}
