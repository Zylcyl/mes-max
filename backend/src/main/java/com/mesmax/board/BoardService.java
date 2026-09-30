package com.mesmax.board;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.entity.*;
import com.mesmax.mapper.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 车间看板聚合服务：把执行层数据折算成车间主任一眼能看懂的口径。 */
@Service
public class BoardService {

    private final MachineStateRegistry registry;
    private final MdMachineMapper machineMapper;
    private final MdWorkCenterMapper workCenterMapper;
    private final MdWorkshopMapper workshopMapper;
    private final ProdOrderMapper orderMapper;
    private final ProdOrderStepMapper stepMapper;
    private final ProdTaskReportMapper reportMapper;
    private final ProdStopRecordMapper stopMapper;
    private final ProdAndonMapper andonMapper;
    private final QcInspectionMapper inspMapper;
    private final QcDefectMapper defectMapper;

    public BoardService(MachineStateRegistry registry, MdMachineMapper machineMapper,
                        MdWorkCenterMapper workCenterMapper, MdWorkshopMapper workshopMapper,
                        ProdOrderMapper orderMapper, ProdOrderStepMapper stepMapper,
                        ProdTaskReportMapper reportMapper, ProdStopRecordMapper stopMapper,
                        ProdAndonMapper andonMapper, QcInspectionMapper inspMapper,
                        QcDefectMapper defectMapper) {
        this.registry = registry;
        this.machineMapper = machineMapper;
        this.workCenterMapper = workCenterMapper;
        this.workshopMapper = workshopMapper;
        this.orderMapper = orderMapper;
        this.stepMapper = stepMapper;
        this.reportMapper = reportMapper;
        this.stopMapper = stopMapper;
        this.andonMapper = andonMapper;
        this.inspMapper = inspMapper;
        this.defectMapper = defectMapper;
    }

    /** 车间看板总览。 */
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        List<ProdTaskReport> todayReports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .eq("work_date", today));
        double output = todayReports.stream()
                .filter(r -> !"开工".equals(r.getReportType()))
                .mapToDouble(r -> nz(r.getQty())).sum();
        double defect = todayReports.stream()
                .filter(r -> !"开工".equals(r.getReportType()))
                .mapToDouble(r -> nz(r.getDefectQty())).sum();
        double runMinutes = todayReports.stream().mapToDouble(r -> nz(r.getRunMinutes())).sum();
        double stopMinutes = todayReports.stream().mapToDouble(r -> nz(r.getStopMinutes())).sum();

        List<ProdOrderStep> todaySteps = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                .ge("plan_start", today.atStartOfDay())
                .lt("plan_start", today.plusDays(1).atStartOfDay()));
        double planQty = todaySteps.stream().mapToDouble(s -> nz(s.getPlanQty())).sum();

        List<MdMachine> machines = machineMapper.selectList(new QueryWrapper<MdMachine>().orderByAsc("id"));
        List<LiveState> live = registry.all();
        Map<String, Integer> statusCount = registry.statusCount();
        long alarmCount = live.stream().filter(s -> "ALARM".equals(s.getStatus())).count();

        List<ProdOrder> orders = orderMapper.selectList(new QueryWrapper<ProdOrder>()
                .in("status", List.of("RELEASED", "SCHEDULED", "IN_PROGRESS", "HOLD"))
                .orderByDesc("priority_score").last("limit 12"));
        List<Map<String, Object>> orderProgress = new ArrayList<>();
        for (ProdOrder o : orders) {
            List<ProdOrderStep> steps = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                    .eq("order_id", o.getId()).orderByAsc("seq_no"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("customerName", o.getCustomerName());
            m.put("productName", o.getProductName());
            m.put("planQty", o.getPlanQty());
            m.put("finishedQty", o.getFinishedQty());
            m.put("priority", o.getPriority());
            m.put("status", o.getStatus());
            m.put("planEnd", o.getPlanEnd());
            m.put("currentProc", steps.stream()
                    .filter(s -> "RUNNING".equals(s.getStatus()) || "INSPECTING".equals(s.getStatus()))
                    .map(ProdOrderStep::getProcName).findFirst().orElse(
                            steps.stream().filter(s -> "DISPATCHED".equals(s.getStatus()))
                                    .map(ProdOrderStep::getProcName).findFirst().orElse(null)));
            m.put("machineCode", steps.stream()
                    .filter(s -> "RUNNING".equals(s.getStatus()))
                    .map(ProdOrderStep::getMachineCode).findFirst().orElse(null));
            // 进度用「工序完成度」表达，而不是数量比：
            // 数量口径下 order.finished_qty 是成品数（最后一道工序产出），在制工单会长期显示 0%。
            int stepTotal = steps.size();
            long stepDone = steps.stream().filter(s -> "FINISHED".equals(s.getStatus())).count();
            double plan = nz(o.getPlanQty());
            m.put("progress", stepTotal == 0 ? 0 : Math.round(stepDone * 100.0 / stepTotal));
            m.put("stepDone", stepDone);
            m.put("stepCount", stepTotal);
            m.put("qtyProgress", plan == 0 ? 0 : Math.min(100, Math.round(nz(o.getFinishedQty()) / plan * 100)));
            m.put("tardy", o.getPlanEnd() != null && o.getPlanEnd().isBefore(LocalDateTime.now())
                    && !"FINISHED".equals(o.getStatus()));
            orderProgress.add(m);
        }

        List<ProdAndon> openAndon = andonMapper.selectList(new QueryWrapper<ProdAndon>()
                .in("status", List.of("待响应", "处理中")).orderByAsc("call_time").last("limit 20"));
        List<QcInspection> todayInsp = inspMapper.selectList(new QueryWrapper<QcInspection>()
                .ge("insp_time", today.atStartOfDay()));

        Map<String, Object> res = new LinkedHashMap<>();
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("machineCount", machines.size());
        kpi.put("runningCount", statusCount.getOrDefault("RUN", 0));
        kpi.put("idleCount", statusCount.getOrDefault("IDLE", 0));
        kpi.put("stopCount", statusCount.getOrDefault("STOP", 0));
        kpi.put("alarmCount", alarmCount);
        kpi.put("offlineCount", statusCount.getOrDefault("OFFLINE", 0));
        kpi.put("todayPlanQty", Math.round(planQty));
        kpi.put("todayOutput", Math.round(output));
        kpi.put("todayDefect", Math.round(defect));
        kpi.put("todayQualifiedRate", output == 0 ? null : Math.round((1 - defect / output) * 1000) / 10.0);
        kpi.put("planRate", planQty == 0 ? null : Math.round(output / planQty * 1000) / 10.0);
        kpi.put("runMinutes", Math.round(runMinutes));
        kpi.put("stopMinutes", Math.round(stopMinutes));
        kpi.put("availability", runMinutes + stopMinutes == 0 ? null
                : Math.round(runMinutes / (runMinutes + stopMinutes) * 1000) / 10.0);
        kpi.put("inspectCount", todayInsp.size());
        kpi.put("openAndon", openAndon.size());
        kpi.put("tardyOrders", orderProgress.stream().filter(m -> Boolean.TRUE.equals(m.get("tardy"))).count());
        kpi.put("sseSubscribers", 0);
        res.put("kpi", kpi);
        res.put("statusCount", statusCount);
        res.put("machineMatrix", live);
        res.put("orderProgress", orderProgress);
        res.put("openAndon", openAndon);
        res.put("alarms", registry.alarms());
        res.put("hourlyOutput", hourlyOutput(today));
        res.put("workCenters", workCenterMapper.selectList(new QueryWrapper<MdWorkCenter>().orderByAsc("id")));
        res.put("workshops", workshopMapper.selectList(new QueryWrapper<MdWorkshop>().orderByAsc("id")));
        res.put("serverTime", LocalDateTime.now());
        return res;
    }

    /** 今日按小时产量（看板底部的节拍柱图）。 */
    public List<Map<String, Object>> hourlyOutput(LocalDate day) {
        List<ProdTaskReport> reports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .eq("work_date", day).orderByAsc("end_time"));
        Map<Integer, double[]> byHour = new TreeMap<>();
        for (int h = 7; h <= 22; h++) byHour.put(h, new double[2]);
        for (ProdTaskReport r : reports) {
            if ("开工".equals(r.getReportType())) continue;
            LocalDateTime t = r.getEndTime() == null ? r.getReportTime() : r.getEndTime();
            if (t == null) continue;
            double[] v = byHour.get(t.getHour());
            if (v == null) continue;
            v[0] += nz(r.getQty());
            v[1] += nz(r.getDefectQty());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        byHour.forEach((h, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour", h);
            m.put("label", String.format("%02d:00", h));
            m.put("qty", Math.round(v[0]));
            m.put("defect", Math.round(v[1]));
            list.add(m);
        });
        return list;
    }

    /** 近 N 天产量与合格率趋势。 */
    public List<Map<String, Object>> trend(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        List<ProdTaskReport> reports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .ge("work_date", from).orderByAsc("work_date"));
        Map<String, double[]> byDay = new TreeMap<>();
        for (int i = 0; i < days; i++) byDay.put(from.plusDays(i).toString(), new double[4]);
        for (ProdTaskReport r : reports) {
            if (r.getWorkDate() == null || "开工".equals(r.getReportType())) continue;
            double[] v = byDay.get(r.getWorkDate().toString());
            if (v == null) continue;
            v[0] += nz(r.getQty());
            v[1] += nz(r.getDefectQty());
            v[2] += nz(r.getRunMinutes());
            v[3] += nz(r.getStopMinutes());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        byDay.forEach((d, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", d);
            m.put("output", Math.round(v[0]));
            m.put("defect", Math.round(v[1]));
            m.put("qualifiedRate", v[0] == 0 ? null : Math.round((1 - v[1] / v[0]) * 1000) / 10.0);
            m.put("runMinutes", Math.round(v[2]));
            m.put("stopMinutes", Math.round(v[3]));
            m.put("availability", v[2] + v[3] == 0 ? null : Math.round(v[2] / (v[2] + v[3]) * 1000) / 10.0);
            list.add(m);
        });
        return list;
    }

    /** 机台实时矩阵（看板主视觉）。 */
    public List<LiveState> machineMatrix() {
        return registry.all();
    }

    /** 停机热点：近 7 天停机时长 TOP 的机台，看板右侧排名用。 */
    public List<Map<String, Object>> stopRanking(int days) {
        LocalDateTime from = LocalDate.now().minusDays(Math.max(1, Math.min(days, 365)) - 1L).atStartOfDay();
        List<ProdStopRecord> stops = stopMapper.selectList(new QueryWrapper<ProdStopRecord>()
                .ge("start_time", from).le("start_time", LocalDateTime.now()));
        Map<String, Double> byMachine = stops.stream()
                .filter(s -> s.getMachineCode() != null)
                .collect(Collectors.groupingBy(ProdStopRecord::getMachineCode,
                        LinkedHashMap::new,
                        Collectors.summingDouble(s -> nz(s.getDurationMin()))));
        List<Map<String, Object>> list = new ArrayList<>();
        byMachine.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("machineCode", e.getKey());
                    m.put("stopMinutes", Math.round(e.getValue()));
                    list.add(m);
                });
        return list;
    }

    /** 质量异常 TOP 机台。 */
    public List<Map<String, Object>> defectRanking(int days) {
        LocalDateTime from = LocalDate.now().minusDays(Math.max(1, Math.min(days, 365)) - 1L).atStartOfDay();
        List<QcDefect> defects = defectMapper.selectList(new QueryWrapper<QcDefect>()
                .ge("create_time", from).le("create_time", LocalDateTime.now()));
        Map<String, Double> byMachine = defects.stream()
                .filter(d -> d.getMachineCode() != null)
                .collect(Collectors.groupingBy(QcDefect::getMachineCode,
                        LinkedHashMap::new, Collectors.summingDouble(d -> nz(d.getDefectQty()))));
        List<Map<String, Object>> list = new ArrayList<>();
        byMachine.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("machineCode", e.getKey());
                    m.put("defectQty", Math.round(e.getValue()));
                    list.add(m);
                });
        return list;
    }

    private static double nz(java.math.BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }
}
