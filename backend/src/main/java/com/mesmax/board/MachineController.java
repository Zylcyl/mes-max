package com.mesmax.board;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.common.BizException;
import com.mesmax.common.R;
import com.mesmax.entity.EqFault;
import com.mesmax.entity.MdMachine;
import com.mesmax.entity.MdWorkCenter;
import com.mesmax.entity.ProdDispatch;
import com.mesmax.entity.ProdMachineStatusLog;
import com.mesmax.entity.ProdMachineTelemetry;
import com.mesmax.entity.ProdStopRecord;
import com.mesmax.entity.ProdTaskReport;
import com.mesmax.entity.ToolLedger;
import com.mesmax.mapper.EqFaultMapper;
import com.mesmax.mapper.MdMachineMapper;
import com.mesmax.mapper.MdWorkCenterMapper;
import com.mesmax.mapper.ProdDispatchMapper;
import com.mesmax.mapper.ProdMachineStatusLogMapper;
import com.mesmax.mapper.ProdMachineTelemetryMapper;
import com.mesmax.mapper.ProdStopRecordMapper;
import com.mesmax.mapper.ProdTaskReportMapper;
import com.mesmax.mapper.ToolLedgerMapper;
import com.mesmax.report.OeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 设备实时监控：单机详情、遥测曲线、状态甘特、单机 OEE。 */
@RestController
@RequestMapping("/machine")
public class MachineController {

    private final MdMachineMapper machineMapper;
    private final MdWorkCenterMapper workCenterMapper;
    private final MachineStateRegistry registry;
    private final ProdMachineTelemetryMapper telemetryMapper;
    private final ProdMachineStatusLogMapper statusLogMapper;
    private final ProdDispatchMapper dispatchMapper;
    private final ProdTaskReportMapper reportMapper;
    private final ProdStopRecordMapper stopMapper;
    private final ToolLedgerMapper toolMapper;
    private final EqFaultMapper faultMapper;
    private final OeeService oeeService;

    public MachineController(MdMachineMapper machineMapper, MdWorkCenterMapper workCenterMapper,
                             MachineStateRegistry registry, ProdMachineTelemetryMapper telemetryMapper,
                             ProdMachineStatusLogMapper statusLogMapper, ProdDispatchMapper dispatchMapper,
                             ProdTaskReportMapper reportMapper, ProdStopRecordMapper stopMapper,
                             ToolLedgerMapper toolMapper, EqFaultMapper faultMapper, OeeService oeeService) {
        this.machineMapper = machineMapper;
        this.workCenterMapper = workCenterMapper;
        this.registry = registry;
        this.telemetryMapper = telemetryMapper;
        this.statusLogMapper = statusLogMapper;
        this.dispatchMapper = dispatchMapper;
        this.reportMapper = reportMapper;
        this.stopMapper = stopMapper;
        this.toolMapper = toolMapper;
        this.faultMapper = faultMapper;
        this.oeeService = oeeService;
    }

    /** 机台列表（主数据 + 实时态合并）。 */
    @GetMapping("/live")
    public R<List<Map<String, Object>>> live(@RequestParam(required = false) Long workshopId,
                                             @RequestParam(required = false) Long workCenterId) {
        QueryWrapper<MdMachine> qw = new QueryWrapper<>();
        if (workshopId != null) qw.eq("workshop_id", workshopId);
        if (workCenterId != null) qw.eq("work_center_id", workCenterId);
        qw.orderByAsc("id");
        Map<Long, String> wcNames = workCenterMapper.selectList(null).stream()
                .collect(Collectors.toMap(MdWorkCenter::getId, MdWorkCenter::getWcName, (a, b) -> a));

        List<Map<String, Object>> list = new ArrayList<>();
        for (MdMachine m : machineMapper.selectList(qw)) {
            LiveState s = registry.get(m.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("macCode", m.getMacCode());
            item.put("macName", m.getMacName());
            item.put("macModel", m.getMacModel());
            item.put("brand", m.getBrand());
            item.put("cncType", m.getCncType());
            item.put("controllerType", m.getControllerType());
            item.put("protocol", m.getProtocol());
            item.put("ipAddr", m.getIpAddr());
            item.put("axisCount", m.getAxisCount());
            item.put("maxSpindleSpeed", m.getMaxSpindleSpeed());
            item.put("toolMagazineCapacity", m.getToolMagazineCapacity());
            item.put("workCenterId", m.getWorkCenterId());
            item.put("workCenterName", wcNames.get(m.getWorkCenterId()));
            item.put("oeeTarget", m.getOeeTarget());
            item.put("status", s == null ? m.getStatus() : s.getStatus());
            item.put("live", s);
            list.add(item);
        }
        return R.ok(list);
    }

    /** 单机详情。 */
    @GetMapping("/{id}/detail")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        MdMachine m = machineMapper.selectById(id);
        if (m == null) throw new BizException("机床不存在");
        LiveState s = registry.get(id);
        MdWorkCenter wc = m.getWorkCenterId() == null ? null : workCenterMapper.selectById(m.getWorkCenterId());
        List<ProdDispatch> dispatches = dispatchMapper.selectList(new QueryWrapper<ProdDispatch>()
                .eq("machine_id", id).in("status", List.of("DISPATCHED", "RECEIVED", "RUNNING"))
                .orderByAsc("plan_start"));
        List<ToolLedger> tools = toolMapper.selectList(new QueryWrapper<ToolLedger>()
                .eq("machine_id", id).orderByAsc("tool_code"));
        List<ProdTaskReport> todayReports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .eq("machine_id", id).eq("work_date", LocalDate.now()).orderByDesc("report_time"));
        List<EqFault> faults = faultMapper.selectList(new QueryWrapper<EqFault>()
                .eq("machine_id", id).orderByDesc("report_time").last("limit 10"));

        double out = todayReports.stream().filter(r -> !"开工".equals(r.getReportType()))
                .mapToDouble(r -> r.getQty() == null ? 0 : r.getQty().doubleValue()).sum();
        double def = todayReports.stream().filter(r -> !"开工".equals(r.getReportType()))
                .mapToDouble(r -> r.getDefectQty() == null ? 0 : r.getDefectQty().doubleValue()).sum();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("machine", m);
        res.put("workCenter", wc);
        res.put("live", s);
        res.put("liveHistory", s == null ? List.of() : s.snapshotHistory());
        res.put("dispatches", dispatches);
        res.put("tools", tools);
        res.put("todayOutput", out);
        res.put("todayDefect", def);
        res.put("todayQualifiedRate", out == 0 ? null : Math.round((1 - def / out) * 1000) / 10.0);
        res.put("faults", faults);
        return R.ok(res);
    }

    /** 遥测历史曲线（默认近 4 小时）。 */
    @GetMapping("/{id}/telemetry")
    public R<List<ProdMachineTelemetry>> telemetry(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "4") int hours) {
        // hours 兜底：负数会让「近 N 小时」变成未来时间点，返回一段无意义的空曲线
        int span = Math.max(1, Math.min(hours, 24 * 30));
        return R.ok(telemetryMapper.selectList(new QueryWrapper<ProdMachineTelemetry>()
                .eq("machine_id", id)
                .ge("ts", LocalDateTime.now().minusHours(span))
                .orderByAsc("ts")));
    }

    /** 状态日志（机床状态甘特）。 */
    @GetMapping("/{id}/statusLogs")
    public R<List<ProdMachineStatusLog>> statusLogs(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "3") int days) {
        int span = Math.max(1, Math.min(days, 365));
        return R.ok(statusLogMapper.selectList(new QueryWrapper<ProdMachineStatusLog>()
                .eq("machine_id", id)
                .ge("start_time", LocalDate.now().minusDays(span - 1L).atStartOfDay())
                .orderByAsc("start_time")));
    }

    /** 状态时长分布。 */
    @GetMapping("/{id}/statusDistribution")
    public R<Map<String, Object>> statusDistribution(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "7") int days) {
        int span = Math.max(1, Math.min(days, 365));
        List<ProdMachineStatusLog> logs = statusLogMapper.selectList(new QueryWrapper<ProdMachineStatusLog>()
                .eq("machine_id", id)
                .ge("start_time", LocalDate.now().minusDays(span - 1L).atStartOfDay()));
        Map<String, Double> byStatus = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (ProdMachineStatusLog l : logs) {
            LocalDateTime end = l.getEndTime() == null ? now : l.getEndTime();
            double min = Duration.between(l.getStartTime(), end).toMinutes();
            if (min < 0) min = 0;
            byStatus.merge(l.getStatus() == null ? "UNKNOWN" : l.getStatus(), min, Double::sum);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("byStatus", byStatus);
        res.put("totalMinutes", Math.round(byStatus.values().stream().mapToDouble(Double::doubleValue).sum()));
        return R.ok(res);
    }

    /**
     * 单机 OEE。
     *
     * <p>刻意委托给 {@link OeeService}，与 {@code /stats/oee} 共用同一份口径 ——
     * 早先这里自己算了一套（性能开动率写死 92%、开工记录又没排除），
     * 导致同一台机在两个接口上给出不同结论。
     */
    @GetMapping("/{id}/oee")
    public R<Map<String, Object>> oee(@PathVariable Long id, @RequestParam(defaultValue = "7") int days) {
        int span = Math.max(1, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(span - 1L);
        return R.ok(oeeService.compute(from, to, id, null));
    }
}
