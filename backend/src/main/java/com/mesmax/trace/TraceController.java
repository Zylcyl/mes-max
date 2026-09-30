package com.mesmax.trace;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.common.BizException;
import com.mesmax.common.R;
import com.mesmax.entity.*;
import com.mesmax.mapper.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 产品谱系追溯。
 *
 * <p>两个方向都要能查，这是 CNC 行业的合规底线：
 * <ul>
 *   <li><b>正向（Forward）</b>：给定序列号/批次 → 查出它经历了哪几道工序、每道工序在哪台机床、
 *       由谁操作、执行哪个 NC 程序、用哪把刀、哪个批次的毛坯、检验结果如何。</li>
 *   <li><b>反向（Backward）</b>：给定毛坯炉号 / 刀具编号 / 机床 / 程序号 →
 *       反查它影响了哪些序列号与工单 —— 客户投诉与召回时用的是这个方向。</li>
 * </ul>
 */
@RestController
@RequestMapping("/trace")
public class TraceController {

    private final TraceSnMapper snMapper;
    private final TraceLogMapper logMapper;
    private final TraceMaterialBatchMapper batchMapper;
    private final QcInspectionMapper inspMapper;
    private final QcDefectMapper defectMapper;
    private final MdMachineMapper machineMapper;
    private final MdEmployeeMapper employeeMapper;
    private final ToolLedgerMapper toolMapper;

    public TraceController(TraceSnMapper snMapper, TraceLogMapper logMapper, TraceMaterialBatchMapper batchMapper,
                           QcInspectionMapper inspMapper, QcDefectMapper defectMapper, MdMachineMapper machineMapper,
                           MdEmployeeMapper employeeMapper, ToolLedgerMapper toolMapper) {
        this.snMapper = snMapper;
        this.logMapper = logMapper;
        this.batchMapper = batchMapper;
        this.inspMapper = inspMapper;
        this.defectMapper = defectMapper;
        this.machineMapper = machineMapper;
        this.employeeMapper = employeeMapper;
        this.toolMapper = toolMapper;
    }

    /** 序列号列表（追溯入口）。 */
    @GetMapping("/sn/page")
    public R<Map<String, Object>> snPage(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String orderNo) {
        QueryWrapper<TraceSn> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("sn_code", keyword).or().like("product_code", keyword)
                    .or().like("product_name", keyword).or().like("batch_no", keyword));
        }
        if (orderNo != null && !orderNo.isBlank()) qw.eq("order_no", orderNo);
        qw.orderByDesc("id");
        var p = snMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), qw);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", p.getTotal());
        res.put("records", p.getRecords());
        return R.ok(res);
    }

    /** 正向追溯：以序列号为入口，返回完整履历。 */
    @GetMapping("/forward/{snCode}")
    public R<Map<String, Object>> forward(@PathVariable String snCode) {
        TraceSn sn = snMapper.selectOne(new QueryWrapper<TraceSn>().eq("sn_code", snCode).last("limit 1"));
        if (sn == null) throw new BizException("未找到该序列号的追溯记录：" + snCode);

        List<TraceLog> logs = logMapper.selectList(new QueryWrapper<TraceLog>()
                .eq("sn_code", snCode).orderByAsc("seq_no").orderByAsc("start_time"));
        List<QcInspection> inspections = inspMapper.selectList(new QueryWrapper<QcInspection>()
                .eq("sn_code", snCode).orderByAsc("insp_time"));
        List<QcDefect> defects = defectMapper.selectList(new QueryWrapper<QcDefect>()
                .eq("sn_code", snCode).orderByDesc("create_time"));
        TraceMaterialBatch batch = sn.getBatchNo() == null ? null
                : batchMapper.selectOne(new QueryWrapper<TraceMaterialBatch>().eq("batch_no", sn.getBatchNo()).last("limit 1"));

        Set<Long> machineIds = logs.stream().map(TraceLog::getMachineId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MdMachine> machineMap = machineIds.isEmpty() ? Map.of()
                : machineMapper.selectBatchIds(machineIds).stream().collect(Collectors.toMap(MdMachine::getId, m -> m));
        Set<Long> empIds = logs.stream().map(TraceLog::getEmployeeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MdEmployee> empMap = empIds.isEmpty() ? Map.of()
                : employeeMapper.selectBatchIds(empIds).stream().collect(Collectors.toMap(MdEmployee::getId, e -> e));

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (TraceLog l : logs) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("log", l);
            MdMachine m = l.getMachineId() == null ? null : machineMap.get(l.getMachineId());
            step.put("machineName", m == null ? null : m.getMacName());
            step.put("cncType", m == null ? null : m.getCncType());
            step.put("controllerType", m == null ? null : m.getControllerType());
            MdEmployee e = l.getEmployeeId() == null ? null : empMap.get(l.getEmployeeId());
            step.put("skillLevel", e == null ? null : e.getSkillLevel());
            timeline.add(step);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("sn", sn);
        res.put("batch", batch);
        res.put("timeline", timeline);
        res.put("inspections", inspections);
        res.put("defects", defects);
        res.put("machineCount", machineMap.size());
        res.put("stepCount", logs.size());
        res.put("operators", logs.stream().map(TraceLog::getOperator).filter(Objects::nonNull).distinct().toList());
        res.put("programs", logs.stream().map(TraceLog::getNcProgramNo).filter(Objects::nonNull).distinct().toList());
        res.put("tools", logs.stream().map(TraceLog::getToolCode).filter(Objects::nonNull).distinct().toList());
        res.put("qualified", !"不合格".equals(sn.getQcResult()));
        return R.ok(res);
    }

    /** 反向追溯：按毛坯批次 / 刀具 / 机床 / 程序号 / 操作工 反查受影响的序列号。 */
    @GetMapping("/backward")
    public R<Map<String, Object>> backward(@RequestParam(required = false) String batchNo,
                                           @RequestParam(required = false) String toolCode,
                                           @RequestParam(required = false) String machineCode,
                                           @RequestParam(required = false) String ncProgramNo,
                                           @RequestParam(required = false) String operator) {
        QueryWrapper<TraceLog> qw = new QueryWrapper<>();
        boolean any = false;
        if (batchNo != null && !batchNo.isBlank()) { qw.eq("material_batch", batchNo); any = true; }
        if (toolCode != null && !toolCode.isBlank()) { qw.eq("tool_code", toolCode); any = true; }
        if (machineCode != null && !machineCode.isBlank()) { qw.eq("machine_code", machineCode); any = true; }
        if (ncProgramNo != null && !ncProgramNo.isBlank()) { qw.eq("nc_program_no", ncProgramNo); any = true; }
        if (operator != null && !operator.isBlank()) { qw.eq("operator", operator); any = true; }
        if (!any) throw new BizException("请至少提供一个查询条件（毛坯批次 / 刀具 / 机床 / 程序号 / 操作工）");
        qw.orderByDesc("start_time").last("limit 1000");
        List<TraceLog> logs = logMapper.selectList(qw);

        List<String> snCodes = logs.stream().map(TraceLog::getSnCode).distinct().toList();
        List<TraceSn> sns = snCodes.isEmpty() ? List.of()
                : snMapper.selectList(new QueryWrapper<TraceSn>().in("sn_code", snCodes));
        Map<String, String> snToHalfOfSn = sns.stream()
                .collect(Collectors.toMap(TraceSn::getSnCode, s -> s.getProductName() == null ? "" : s.getProductName(),
                        (a, b) -> a));
        Map<String, Long> byOrder = sns.stream()
                .collect(Collectors.groupingBy(s -> s.getOrderNo() == null ? "-" : s.getOrderNo(),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byMachine = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getMachineCode() == null ? "-" : l.getMachineCode(),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byProc = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getProcName() == null ? "-" : l.getProcName(),
                        LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("condition", Map.of(
                "batchNo", nvl(batchNo), "toolCode", nvl(toolCode), "machineCode", nvl(machineCode),
                "ncProgramNo", nvl(ncProgramNo), "operator", nvl(operator)));
        res.put("recordCount", logs.size());
        res.put("snCount", snCodes.size());
        res.put("snCodes", snCodes);
        res.put("affectedOrders", byOrder);
        res.put("byMachine", byMachine);
        res.put("byProc", byProc);
        res.put("products", sns.stream().map(TraceSn::getProductName).filter(Objects::nonNull).distinct().toList());
        res.put("records", logs);
        res.put("sns", sns);
        res.put("productOfSn", snToHalfOfSn);
        return R.ok(res);
    }

    /** 毛坯批次列表（含去向统计）。 */
    @GetMapping("/batch/list")
    public R<List<Map<String, Object>>> batches() {
        List<TraceMaterialBatch> batches = batchMapper.selectList(new QueryWrapper<TraceMaterialBatch>().orderByDesc("in_time"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (TraceMaterialBatch b : batches) {
            List<TraceLog> used = logMapper.selectList(new QueryWrapper<TraceLog>()
                    .eq("material_batch", b.getBatchNo()).last("limit 500"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("batch", b);
            m.put("usedSnCount", used.stream().map(TraceLog::getSnCode).distinct().count());
            m.put("usedOrderCount", used.stream().map(TraceLog::getOrderNo).distinct().count());
            m.put("status", used.isEmpty() ? "未投产" : "已投产");
            list.add(m);
        }
        return R.ok(list);
    }

    /** 追溯总览：序列号总数、已完工数、涉及批次、异常序列号。 */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        List<TraceSn> all = snMapper.selectList(new QueryWrapper<TraceSn>().orderByDesc("id").last("limit 2000"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", all.size());
        res.put("finished", all.stream().filter(s -> "完工".equals(s.getStatus())).count());
        res.put("inProgress", all.stream().filter(s -> "在制".equals(s.getStatus())).count());
        res.put("scrapped", all.stream().filter(s -> "报废".equals(s.getStatus())).count());
        res.put("retest", all.stream().filter(s -> "待复检".equals(s.getQcResult())).count());
        res.put("byOrder", all.stream().collect(Collectors.groupingBy(
                s -> s.getOrderNo() == null ? "-" : s.getOrderNo(), LinkedHashMap::new, Collectors.counting())));
        res.put("recent", all.stream().limit(50).toList());
        res.put("batches", batches().getData());
        return R.ok(res);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
