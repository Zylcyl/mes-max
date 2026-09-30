package com.mesmax.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.common.BizException;
import com.mesmax.common.CodeGen;
import com.mesmax.common.R;
import com.mesmax.common.UserContext;
import com.mesmax.entity.ProdAndon;
import com.mesmax.entity.ProdDispatch;
import com.mesmax.entity.ProdStopRecord;
import com.mesmax.entity.ProdTaskReport;
import com.mesmax.entity.ToolLedger;
import com.mesmax.mapper.ProdAndonMapper;
import com.mesmax.mapper.ProdDispatchMapper;
import com.mesmax.mapper.ProdTaskReportMapper;
import com.mesmax.mapper.ToolLedgerMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 车间报工终端 —— 面向机台旁的触屏电脑/工控一体机。
 *
 * <p>交互原则：按钮大、信息少、以「机台」为第一视角（而不是以订单为视角），
 * 因为操作工只关心「我这台机床现在该干什么」。
 */
@RestController
@RequestMapping("/terminal")
public class TerminalController {

    private final ProductionService productionService;
    private final ProdDispatchMapper dispatchMapper;
    private final ProdTaskReportMapper reportMapper;
    private final ProdAndonMapper andonMapper;
    private final ToolLedgerMapper toolMapper;

    public TerminalController(ProductionService productionService, ProdDispatchMapper dispatchMapper,
                              ProdTaskReportMapper reportMapper,
                              ProdAndonMapper andonMapper, ToolLedgerMapper toolMapper) {
        this.productionService = productionService;
        this.dispatchMapper = dispatchMapper;
        this.reportMapper = reportMapper;
        this.andonMapper = andonMapper;
        this.toolMapper = toolMapper;
    }

    /** 当前机台的任务列表（待接单 / 进行中 / 今日已完工）。 */
    @GetMapping("/tasks")
    public R<Map<String, Object>> tasks(@RequestParam Long machineId,
                                        @RequestParam(required = false) Long employeeId) {
        List<ProdDispatch> all = dispatchMapper.selectList(new QueryWrapper<ProdDispatch>()
                .eq("machine_id", machineId).orderByAsc("plan_start").last("limit 100"));
        List<ProdDispatch> pending = all.stream()
                .filter(d -> List.of("DISPATCHED", "RECEIVED", "RUNNING").contains(d.getStatus())).toList();

        List<ProdTaskReport> reports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .eq("machine_id", machineId)
                .eq("work_date", LocalDate.now())
                .orderByDesc("report_time").last("limit 50"));

        /*
         * 「今日已完工」必须按实际完工动作判定，而不是按计划开工日期 ——
         * 早先用 plan_start 的日期筛选，一个计划昨天开工、今天才完工的任务会从列表里消失，
         * 而一个「计划今天开工、实际今天没做完」的任务又会永远挂在完工列表里。
         *
         * 判定依据是「今天有报工记录」且「派工单已 FINISHED」：
         * 工序报满计划量时会自动完工（那条记录的 report_type 是「报工」而不是「完工」），
         * 只看 report_type='完工' 会把这类正常完工的工票漏掉。
         */
        Set<Long> finishedToday = reports.stream()
                .filter(r -> r.getDispatchId() != null && !"开工".equals(r.getReportType()))
                .map(ProdTaskReport::getDispatchId)
                .collect(Collectors.toSet());
        List<ProdDispatch> today = all.stream()
                .filter(d -> "FINISHED".equals(d.getStatus()) && finishedToday.contains(d.getId())).toList();

        double todayQty = reports.stream().filter(r -> !"开工".equals(r.getReportType()))
                .mapToDouble(r -> r.getQty() == null ? 0 : r.getQty().doubleValue()).sum();
        double todayDefect = reports.stream().filter(r -> !"开工".equals(r.getReportType()))
                .mapToDouble(r -> r.getDefectQty() == null ? 0 : r.getDefectQty().doubleValue()).sum();

        List<ProdAndon> andons = andonMapper.selectList(new QueryWrapper<ProdAndon>()
                .eq("machine_id", machineId).in("status", List.of("待响应", "处理中"))
                .orderByDesc("call_time"));

        List<ToolLedger> tools = toolMapper.selectList(new QueryWrapper<ToolLedger>()
                .eq("machine_id", machineId).orderByAsc("tool_code"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", pending);
        m.put("finishedToday", today);
        m.put("reports", reports);
        m.put("todayQty", todayQty);
        m.put("todayDefect", todayDefect);
        m.put("todayQualifiedRate", todayQty == 0 ? null : Math.round((1 - todayDefect / todayQty) * 1000) / 10.0);
        m.put("openAndons", andons);
        m.put("tools", tools);
        return R.ok(m);
    }

    /** 开工（含 NC 程序号校验：程序不符直接拒绝开工）。 */
    @PostMapping("/start")
    public R<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        Long dispatchId = longOf(body, "dispatchId");
        if (dispatchId == null) throw new BizException("缺少派工单编号 dispatchId");
        String program = body.get("ncProgramNo") == null ? null : String.valueOf(body.get("ncProgramNo"));
        return R.ok("已开工，开始计时", productionService.startWork(dispatchId, program));
    }

    /** 报工（可多次累积，达到计划数量自动完工）。 */
    @PostMapping("/report")
    public R<Map<String, Object>> report(@RequestBody ProductionService.ReportRequest req) {
        if (req.dispatchId() == null) throw new BizException("缺少派工单");
        return R.ok("报工成功", productionService.reportWork(req, false));
    }

    /** 完工报工。 */
    @PostMapping("/finish")
    public R<Map<String, Object>> finish(@RequestBody ProductionService.ReportRequest req) {
        if (req.dispatchId() == null) throw new BizException("缺少派工单");
        return R.ok("完工已提交", productionService.reportWork(req, true));
    }

    /** 记录停机（换刀 / 换型 / 故障 / 待料）。 */
    @PostMapping("/stop")
    public R<ProdStopRecord> stop(@RequestBody Map<String, Object> body) {
        ProdStopRecord s = new ProdStopRecord();
        s.setMachineId(longOf(body, "machineId"));
        s.setMachineCode(str(body.get("machineCode")));
        s.setOrderId(longOf(body, "orderId"));
        s.setOrderNo(str(body.get("orderNo")));
        s.setStepId(longOf(body, "stepId"));
        s.setStopType(str(body.get("stopType")) == null ? "非计划停机" : str(body.get("stopType")));
        s.setReasonCode(str(body.get("reasonCode")));
        s.setReasonDesc(str(body.get("reasonDesc")));
        s.setStartTime(timeOf(body, "startTime", LocalDateTime.now()));
        s.setEndTime(timeOf(body, "endTime", LocalDateTime.now()));
        s.setHandler(UserContext.realName());
        s.setShiftName(str(body.get("shiftName")));
        s.setRemark(str(body.get("remark")));
        if (body.get("durationMin") != null) s.setDurationMin(new BigDecimal(String.valueOf(body.get("durationMin"))));
        return R.ok("停机已记录", productionService.saveStop(s));
    }

    /** 呼叫安灯。 */
    @PostMapping("/andon")
    public R<ProdAndon> andon(@RequestBody Map<String, Object> body) {
        ProdAndon a = new ProdAndon();
        a.setAndonNo(CodeGen.next("AN"));
        a.setMachineId(longOf(body, "machineId"));
        a.setMachineCode(str(body.get("machineCode")));
        a.setOrderId(longOf(body, "orderId"));
        a.setOrderNo(str(body.get("orderNo")));
        a.setAndonType(str(body.get("andonType")) == null ? "设备异常" : str(body.get("andonType")));
        a.setLevel(str(body.get("level")) == null ? "一级" : str(body.get("level")));
        a.setContent(str(body.get("content")));
        a.setCallUser(UserContext.realName());
        a.setCallTime(LocalDateTime.now());
        a.setStatus("待响应");
        andonMapper.insert(a);
        return R.ok("已呼叫，请等待响应", a);
    }

    /**
     * 机台状态快速切换（待机 / 保养 / 离线）。
     *
     * <p>复用 {@link ProductionService#writeStatusLog}：它会同时关掉上一条未结束的状态记录、
     * 写新记录，并把 {@code md_machine.run_state} 同步过去。原来这里自己写日志但没同步主数据，
     * 导致「主数据显示运行中、状态日志显示保养中」。
     */
    @PostMapping("/machineState")
    public R<Void> machineState(@RequestBody Map<String, Object> body) {
        Long machineId = longOf(body, "machineId");
        if (machineId == null) throw new BizException("缺少机床编号 machineId");
        String state = String.valueOf(body.get("state"));
        productionService.writeStatusLog(machineId, str(body.get("machineCode")), state,
                str(body.get("reason")) == null ? "终端手动切换状态" : str(body.get("reason")), null, null);
        return R.ok("状态已切换为 " + state, null);
    }

    /** 本机台今日产量节拍（按小时），用于终端上的迷你趋势条。 */
    @GetMapping("/hourly/{machineId}")
    public R<List<Map<String, Object>>> hourly(@PathVariable Long machineId) {
        List<ProdTaskReport> reports = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .eq("machine_id", machineId).eq("work_date", LocalDate.now()));
        Map<Integer, Double> byHour = new java.util.TreeMap<>();
        for (int h = 7; h <= 22; h++) byHour.put(h, 0.0);
        for (ProdTaskReport r : reports) {
            LocalDateTime t = r.getEndTime() == null ? r.getReportTime() : r.getEndTime();
            if (t == null) continue;
            int h = t.getHour();
            byHour.merge(h, r.getQty() == null ? 0 : r.getQty().doubleValue(), Double::sum);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        byHour.forEach((h, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour", h);
            m.put("qty", v);
            list.add(m);
        });
        return R.ok(list);
    }

    private static String str(Object o) {
        return o == null || "null".equals(String.valueOf(o)) ? null : String.valueOf(o);
    }

    /** 宽松取整型参数：空/null 返回 null，格式错误直接告诉调用方哪里写错了（而不是抛 500）。 */
    private static Long longOf(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new BizException("参数 " + key + " 必须是整数，实际收到：" + s);
        }
    }

    /** 宽松取时间参数：支持 {@code 2026-09-30 08:30:00} 与 ISO 的 {@code 2026-09-30T08:30:00}。 */
    private static LocalDateTime timeOf(Map<String, Object> body, String key, LocalDateTime fallback) {
        Object v = body.get(key);
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return fallback;
        try {
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (java.time.format.DateTimeParseException e) {
            throw new BizException("参数 " + key + " 的时间格式不正确：" + s + "（应形如 2026-09-30 08:30:00）");
        }
    }
}
