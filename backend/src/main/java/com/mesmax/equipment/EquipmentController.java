package com.mesmax.equipment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.common.BizException;
import com.mesmax.common.CodeGen;
import com.mesmax.common.PageResult;
import com.mesmax.common.R;
import com.mesmax.common.UserContext;
import com.mesmax.entity.*;
import com.mesmax.mapper.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备管理（点检 / 保养 / 故障）。
 *
 * <p>CNC 设备的三大可靠性与维护指标在此计算：
 * <ul>
 *   <li><b>MTTR</b>（平均修复时间）= 总停机时长 / 故障次数 —— 反映「修得快不快」</li>
 *   <li><b>MTBF</b>（平均故障间隔）= 累计运行时长 / 故障次数 —— 反映「坏得勤不勤」</li>
 *   <li><b>点检异常率</b> = 异常项 / 点检项 —— 反映「隐患发现得早不早」</li>
 * </ul>
 */
@RestController
@RequestMapping("/equipment")
public class EquipmentController {

    private final EqCheckItemMapper checkItemMapper;
    private final EqCheckRecordMapper checkRecordMapper;
    private final EqMaintPlanMapper maintPlanMapper;
    private final EqMaintOrderMapper maintOrderMapper;
    private final EqFaultMapper faultMapper;
    private final MdMachineMapper machineMapper;
    private final ProdMachineStatusLogMapper statusLogMapper;
    private final ProdStopRecordMapper stopMapper;

    public EquipmentController(EqCheckItemMapper checkItemMapper, EqCheckRecordMapper checkRecordMapper,
                               EqMaintPlanMapper maintPlanMapper, EqMaintOrderMapper maintOrderMapper,
                               EqFaultMapper faultMapper, MdMachineMapper machineMapper,
                               ProdMachineStatusLogMapper statusLogMapper, ProdStopRecordMapper stopMapper) {
        this.checkItemMapper = checkItemMapper;
        this.checkRecordMapper = checkRecordMapper;
        this.maintPlanMapper = maintPlanMapper;
        this.maintOrderMapper = maintOrderMapper;
        this.faultMapper = faultMapper;
        this.machineMapper = machineMapper;
        this.statusLogMapper = statusLogMapper;
        this.stopMapper = stopMapper;
    }

    // ---------------- 设备总览 ----------------

    @GetMapping("/overview")
    public R<Map<String, Object>> overview(@RequestParam(defaultValue = "30") int days) {
        int span = Math.max(1, Math.min(days, 730));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        List<EqFault> faults = faultMapper.selectList(new QueryWrapper<EqFault>()
                .ge("report_time", from).le("report_time", now));
        List<EqCheckRecord> checks = checkRecordMapper.selectList(new QueryWrapper<EqCheckRecord>()
                .ge("check_time", from).le("check_time", now));
        List<EqMaintPlan> plans = maintPlanMapper.selectList(new QueryWrapper<EqMaintPlan>().orderByAsc("next_date"));
        List<MdMachine> machines = machineMapper.selectList(new QueryWrapper<MdMachine>().orderByAsc("id"));

        double totalDown = faults.stream().mapToDouble(f -> downMinutes(f, now)).sum();
        Map<String, Double> runMinutes = new HashMap<>();
        for (ProdMachineStatusLog log : statusLogMapper.selectList(new QueryWrapper<ProdMachineStatusLog>()
                .ge("start_time", from).le("start_time", now))) {
            if (!"RUN".equals(log.getStatus()) || log.getStartTime() == null) continue;
            /*
             * 累计运行时长 = 「每条 RUN 状态记录的实际时长」之和，而不是直接取 duration_sec：
             * 未结束的记录（end_time 为空）那一列还是 NULL，原实现按 0 计 ——
             * 只要机床此刻正在运行，正在跑的这一段就永远不计入 MTBF，指标被系统性低估。
             */
            LocalDateTime begin = log.getStartTime().isBefore(from) ? from : log.getStartTime();
            LocalDateTime end = log.getEndTime() == null ? now : log.getEndTime();
            double mins = Math.max(0, Duration.between(begin, end).toMinutes());
            runMinutes.merge(log.getMachineCode(), mins, Double::sum);
        }
        double totalRun = runMinutes.values().stream().mapToDouble(Double::doubleValue).sum();

        Map<String, Long> faultByType = faults.stream()
                .collect(Collectors.groupingBy(f -> f.getFaultType() == null ? "其他" : f.getFaultType(),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Double> downByMachine = faults.stream()
                .filter(f -> f.getMachineCode() != null)
                .collect(Collectors.groupingBy(EqFault::getMachineCode,
                        LinkedHashMap::new,
                        Collectors.summingDouble(f -> downMinutes(f, now))));

        long abnormalChecks = checks.stream().filter(c -> "异常".equals(c.getResult())).count();
        List<EqMaintPlan> due = plans.stream()
                .filter(p -> p.getNextDate() != null && !p.getNextDate().isAfter(LocalDate.now().plusDays(7)))
                .toList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("machines", machines);
        res.put("machineCount", machines.size());
        res.put("days", span);
        res.put("faultCount", faults.size());
        res.put("mttr", faults.isEmpty() ? 0 : Math.round(totalDown / faults.size() * 10) / 10.0);
        res.put("mtbf", faults.isEmpty() ? 0 : Math.round(totalRun / faults.size() * 10) / 10.0);
        res.put("totalDownMinutes", Math.round(totalDown));
        res.put("totalRunMinutes", Math.round(totalRun));
        res.put("checkCount", checks.size());
        res.put("abnormalCheckCount", abnormalChecks);
        res.put("checkAbnormalRate", checks.isEmpty() ? 0 : Math.round(abnormalChecks * 1000.0 / checks.size()) / 10.0);
        res.put("faultByType", faultByType);
        res.put("downByMachine", downByMachine);
        res.put("maintDue", due);
        res.put("openFaults", faults.stream().filter(f -> !"已修复".equals(f.getStatus())).toList());
        res.put("faults", faults.stream().sorted(Comparator.comparing(EqFault::getReportTime).reversed()).toList());
        return R.ok(res);
    }

    /**
     * 单条故障的停机时长（分钟）。
     *
     * <p>已修复的故障用落库的 down_minutes；还没修完的按「报告时刻 → 此刻」实时折算 ——
     * 否则正在停机的那台机对 MTTR 是「零贡献」，MTTR 越好说明停得越多，完全反了。
     */
    private static double downMinutes(EqFault f, LocalDateTime now) {
        if (f.getDownMinutes() != null) return f.getDownMinutes().doubleValue();
        if (f.getReportTime() == null) return 0;
        LocalDateTime end = f.getRepairEndTime() == null ? now : f.getRepairEndTime();
        return Math.max(0, Duration.between(f.getReportTime(), end).toMinutes());
    }

    // ---------------- 点检 ----------------

    @GetMapping("/checkItem/list")
    public R<List<EqCheckItem>> checkItems() {
        return R.ok(checkItemMapper.selectList(new QueryWrapper<EqCheckItem>()
                .orderByAsc("sort_no").orderByAsc("id")));
    }

    @PostMapping("/checkItem/save")
    public R<EqCheckItem> saveCheckItem(@RequestBody EqCheckItem item) {
        if (item.getId() == null) checkItemMapper.insert(item);
        else checkItemMapper.updateById(item);
        return R.ok("保存成功", item);
    }

    @DeleteMapping("/checkItem/delete/{id}")
    public R<Void> deleteCheckItem(@PathVariable Long id) {
        checkItemMapper.deleteById(id);
        return R.ok("删除成功", null);
    }

    @GetMapping("/checkRecord/page")
    public R<PageResult<EqCheckRecord>> checkRecords(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) Long machineId,
                                                     @RequestParam(required = false) String result) {
        QueryWrapper<EqCheckRecord> qw = new QueryWrapper<>();
        if (machineId != null) qw.eq("machine_id", machineId);
        if (result != null && !result.isBlank()) qw.eq("result", result);
        qw.orderByDesc("check_time");
        var p = checkRecordMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    @PostMapping("/checkRecord/save")
    public R<EqCheckRecord> saveCheckRecord(@RequestBody EqCheckRecord r) {
        if (r.getCheckTime() == null) r.setCheckTime(LocalDateTime.now());
        if (r.getChecker() == null) r.setChecker(UserContext.realName());
        if (r.getId() == null) checkRecordMapper.insert(r);
        else checkRecordMapper.updateById(r);
        return R.ok("点检已提交", r);
    }

    /** 一张班前点检表：按机台类型带出应当点检的项目。 */
    @GetMapping("/checkSheet/{machineId}")
    public R<Map<String, Object>> checkSheet(@PathVariable Long machineId) {
        MdMachine m = machineMapper.selectById(machineId);
        if (m == null) throw new BizException("机床不存在");
        List<EqCheckItem> items = checkItemMapper.selectList(new QueryWrapper<EqCheckItem>()
                .and(w -> w.eq("mac_type", "ALL").or().eq("mac_type", m.getCncType()))
                .orderByAsc("sort_no"));
        List<EqCheckRecord> today = checkRecordMapper.selectList(new QueryWrapper<EqCheckRecord>()
                .eq("machine_id", machineId)
                .ge("check_time", LocalDate.now().atStartOfDay()));
        Map<Long, EqCheckRecord> done = today.stream()
                .collect(Collectors.toMap(EqCheckRecord::getItemId, x -> x, (a, b) -> b));
        List<Map<String, Object>> list = new ArrayList<>();
        for (EqCheckItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", item);
            row.put("record", done.get(item.getId()));
            row.put("checked", done.containsKey(item.getId()));
            list.add(row);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("machine", m);
        res.put("rows", list);
        res.put("total", items.size());
        res.put("doneCount", done.size());
        return R.ok(res);
    }

    // ---------------- 保养 ----------------

    @GetMapping("/maintPlan/page")
    public R<PageResult<EqMaintPlan>> maintPlans(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        var p = maintPlanMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new QueryWrapper<EqMaintPlan>().orderByAsc("next_date"));
        return R.ok(PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    @PostMapping("/maintPlan/save")
    public R<EqMaintPlan> saveMaintPlan(@RequestBody EqMaintPlan plan) {
        if (plan.getPlanCode() == null || plan.getPlanCode().isBlank()) plan.setPlanCode(CodeGen.next("MP"));
        if (plan.getId() == null) maintPlanMapper.insert(plan);
        else maintPlanMapper.updateById(plan);
        return R.ok("保存成功", plan);
    }

    /** 由保养计划生成保养工单。 */
    @PostMapping("/maintPlan/generate/{id}")
    @Transactional
    public R<EqMaintOrder> generateMaintOrder(@PathVariable Long id) {
        EqMaintPlan plan = maintPlanMapper.selectById(id);
        if (plan == null) throw new BizException("保养计划不存在");
        LocalDate base = plan.getNextDate() == null ? LocalDate.now() : plan.getNextDate();
        EqMaintOrder o = new EqMaintOrder();
        o.setMaintNo(CodeGen.next("MT"));
        o.setPlanId(plan.getId());
        o.setMachineId(plan.getMachineId());
        o.setMachineCode(plan.getMachineCode());
        o.setMaintType(plan.getMaintType());
        o.setMaintainer(plan.getPrincipal());
        o.setPlanStart(base.atTime(8, 30));
        o.setPlanEnd(base.atTime(16, 30));
        o.setSpareParts(plan.getContent());
        o.setStatus("待执行");
        maintOrderMapper.insert(o);
        return R.ok("保养工单已生成：" + o.getMaintNo(), o);
    }

    @GetMapping("/maintOrder/page")
    public R<PageResult<EqMaintOrder>> maintOrders(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   @RequestParam(required = false) String status) {
        QueryWrapper<EqMaintOrder> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByDesc("plan_start");
        var p = maintOrderMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    /** 完成保养工单：回写计划的「上次保养日期 / 下次保养日期」。 */
    @PostMapping("/maintOrder/finish/{id}")
    @Transactional
    public R<EqMaintOrder> finishMaintOrder(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        EqMaintOrder o = maintOrderMapper.selectById(id);
        if (o == null) throw new BizException("保养工单不存在");
        LocalDateTime now = LocalDateTime.now();
        o.setActualStart(body != null && body.get("actualStart") != null
                ? LocalDateTime.parse(String.valueOf(body.get("actualStart"))) : now.minusHours(4));
        o.setActualEnd(now);
        o.setDownMinutes(BigDecimal.valueOf(Duration.between(o.getActualStart(), now).toMinutes()));
        o.setResult(body != null && body.get("result") != null ? String.valueOf(body.get("result"))
                : "按保养项目执行完成，设备精度校验合格");
        o.setCost(body != null && body.get("cost") != null ? new BigDecimal(String.valueOf(body.get("cost"))) : null);
        o.setStatus("已完成");
        maintOrderMapper.updateById(o);

        EqMaintPlan plan = o.getPlanId() == null ? null : maintPlanMapper.selectById(o.getPlanId());
        if (plan != null) {
            plan.setLastDate(now.toLocalDate());
            plan.setNextDate(nextDate(now.toLocalDate(), plan.getPeriodType(), plan.getPeriodValue()));
            plan.setStatus("正常");
            maintPlanMapper.updateById(plan);
        }
        return R.ok("保养已完成", o);
    }

    private LocalDate nextDate(LocalDate base, String periodType, Integer value) {
        int v = value == null || value <= 0 ? 1 : value;
        return switch (periodType == null ? "月" : periodType) {
            case "季" -> base.plusMonths(3L * v);
            case "年" -> base.plusYears(v);
            case "周" -> base.plusWeeks(v);
            default -> base.plusMonths(v);
        };
    }

    // ---------------- 故障 ----------------

    @GetMapping("/fault/page")
    public R<PageResult<EqFault>> faults(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) Long machineId,
                                         @RequestParam(required = false) String status) {
        QueryWrapper<EqFault> qw = new QueryWrapper<>();
        if (machineId != null) qw.eq("machine_id", machineId);
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByDesc("report_time");
        var p = faultMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    @PostMapping("/fault/save")
    public R<EqFault> saveFault(@RequestBody EqFault f) {
        if (f.getFaultNo() == null || f.getFaultNo().isBlank()) f.setFaultNo(CodeGen.next("FT"));
        if (f.getReportTime() == null) f.setReportTime(LocalDateTime.now());
        if (f.getReportUser() == null) f.setReportUser(UserContext.realName());
        if (f.getStatus() == null) f.setStatus("待维修");
        if (f.getId() == null) faultMapper.insert(f);
        else faultMapper.updateById(f);
        return R.ok("故障已登记", f);
    }

    /** 响应故障（开始维修）。 */
    @PostMapping("/fault/respond/{id}")
    public R<EqFault> respondFault(@PathVariable Long id) {
        EqFault f = require(id);
        f.setRespondTime(LocalDateTime.now());
        f.setRepairUser(UserContext.realName());
        f.setStatus("维修中");
        faultMapper.updateById(f);
        return R.ok("已响应，开始维修", f);
    }

    /** 修复完成，计算停机时长（MTTR 的原始数据）。 */
    @PostMapping("/fault/close/{id}")
    @Transactional
    public R<EqFault> closeFault(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        EqFault f = require(id);
        LocalDateTime now = LocalDateTime.now();
        f.setRepairEndTime(now);
        if (f.getRespondTime() == null) f.setRespondTime(now);
        f.setDownMinutes(BigDecimal.valueOf(Duration.between(f.getReportTime(), now).toMinutes()));
        if (body.get("cause") != null) f.setCause(String.valueOf(body.get("cause")));
        if (body.get("solution") != null) f.setSolution(String.valueOf(body.get("solution")));
        if (body.get("spareParts") != null) f.setSpareParts(String.valueOf(body.get("spareParts")));
        f.setRepairUser(UserContext.realName());
        f.setStatus("已修复");
        faultMapper.updateById(f);

        // 同步写一条停机记录，保证 OEE 的时间损失口径一致
        ProdStopRecord s = new ProdStopRecord();
        s.setMachineId(f.getMachineId());
        s.setMachineCode(f.getMachineCode());
        s.setStopType("非计划停机");
        s.setReasonCode("FAULT");
        s.setReasonDesc(f.getFaultDesc());
        s.setStartTime(f.getReportTime());
        s.setEndTime(now);
        s.setDurationMin(f.getDownMinutes());
        s.setHandler(f.getRepairUser());
        s.setStatus("已关闭");
        s.setRemark("由故障单 " + f.getFaultNo() + " 自动生成");
        stopMapper.insert(s);
        return R.ok("故障已修复关闭", f);
    }

    private EqFault require(Long id) {
        EqFault f = faultMapper.selectById(id);
        if (f == null) throw new BizException("故障单不存在");
        return f;
    }
}
