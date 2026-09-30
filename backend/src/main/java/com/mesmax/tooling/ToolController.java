package com.mesmax.tooling;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.*;
import com.mesmax.entity.*;
import com.mesmax.mapper.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 刀具管理。
 *
 * <p>刀具是 CNC 车间唯一「既影响质量、又影响停机、还直接烧钱」的对象，
 * 因此本模块不止做台账，而是打通三条链路：
 * <ol>
 *   <li><b>寿命链路</b>：装刀 → 加工累计 → 寿命预警 → 卸刀 → 刃磨复位，让崩刃撞机变成可预防。</li>
 *   <li><b>对刀链路</b>：刀补号 / 刀尖圆弧半径 / 刀具长度的测量与偏差留痕，避免「刀补错 = 批量报废」。</li>
 *   <li><b>追溯链路</b>：一把刀加工过哪些工单、哪些序列号，可正反向查询。</li>
 * </ol>
 */
@RestController
@RequestMapping("/tool")
public class ToolController extends BaseCrudController<ToolLedger> {

    private final ToolLedgerMapper mapper;
    private final ToolUsageMapper usageMapper;
    private final ToolGrindMapper grindMapper;
    private final ToolMeasureMapper measureMapper;
    private final MdMachineMapper machineMapper;
    private final TraceLogMapper traceLogMapper;

    public ToolController(ToolLedgerMapper mapper, ToolUsageMapper usageMapper, ToolGrindMapper grindMapper,
                          ToolMeasureMapper measureMapper, MdMachineMapper machineMapper,
                          TraceLogMapper traceLogMapper) {
        this.mapper = mapper;
        this.usageMapper = usageMapper;
        this.grindMapper = grindMapper;
        this.measureMapper = measureMapper;
        this.machineMapper = machineMapper;
        this.traceLogMapper = traceLogMapper;
    }

    @Override
    protected BaseMapper<ToolLedger> mapper() {
        return mapper;
    }

    @Override
    protected void beforeCreate(ToolLedger entity) {
        if (entity.getToolCode() == null || entity.getToolCode().isBlank()) {
            entity.setToolCode(CodeGen.next("TL"));
        }
        if (entity.getUsedLifeMin() == null) entity.setUsedLifeMin(BigDecimal.ZERO);
        if (entity.getUsedQty() == null) entity.setUsedQty(0);
        entity.setLifeRatio(ratio(entity));
    }

    /**
     * 更新刀具时只重算「寿命占用率」，且必须能拿到标准寿命与已用寿命（本次入参或库中现值）。
     *
     * <p>早先更新走的是新增逻辑：{@code POST /tool/save {"id":1,"toolCode":"X"}} 这种局部更新
     * 会因为入参缺 stdLifeMin 而被当成「标准寿命为 0」，把 life_ratio 直接写成 0，
     * 刀具于是静默退出了寿命预警。
     */
    @Override
    protected void beforeUpdate(ToolLedger entity) {
        ToolLedger old = entity.getId() == null ? null : mapper.selectById(entity.getId());
        BigDecimal std = entity.getStdLifeMin() != null ? entity.getStdLifeMin()
                : (old == null ? null : old.getStdLifeMin());
        BigDecimal used = entity.getUsedLifeMin() != null ? entity.getUsedLifeMin()
                : (old == null ? null : old.getUsedLifeMin());
        if (std != null && std.doubleValue() > 0 && used != null) {
            entity.setLifeRatio(BigDecimal.valueOf(used.doubleValue() / std.doubleValue() * 100)
                    .setScale(2, RoundingMode.HALF_UP));
        }
        // 拿不到完整输入时保持原值不动（updateById 会跳过 null 字段）
    }

    private BigDecimal ratio(ToolLedger t) {
        double std = t.getStdLifeMin() == null ? 0 : t.getStdLifeMin().doubleValue();
        if (std <= 0) return BigDecimal.ZERO;
        double used = t.getUsedLifeMin() == null ? 0 : t.getUsedLifeMin().doubleValue();
        return BigDecimal.valueOf(used / std * 100).setScale(2, RoundingMode.HALF_UP);
    }

    /** 刀具总览：状态分布、类型分布、寿命预警 TOP、机台在用刀。 */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        List<ToolLedger> all = mapper.selectList(new QueryWrapper<ToolLedger>().orderByDesc("life_ratio"));
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Double> valueByMachine = new LinkedHashMap<>();
        for (ToolLedger t : all) {
            byStatus.merge(t.getStatus() == null ? "未知" : t.getStatus(), 1, Integer::sum);
            byType.merge(t.getToolType() == null ? "其他" : t.getToolType(), 1, Integer::sum);
            if (t.getMachineCode() != null && t.getUnitPrice() != null) {
                valueByMachine.merge(t.getMachineCode(), t.getUnitPrice().doubleValue(), Double::sum);
            }
        }
        List<ToolLedger> warning = all.stream()
                .filter(t -> t.getLifeRatio() != null && t.getLifeRatio().doubleValue() >= 85)
                .sorted((a, b) -> b.getLifeRatio().compareTo(a.getLifeRatio()))
                .toList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", all.size());
        res.put("inUse", all.stream().filter(t -> "在用".equals(t.getStatus())).count());
        res.put("inStock", all.stream().filter(t -> "在库".equals(t.getStatus())).count());
        res.put("warningCount", warning.size());
        res.put("pendingGrind", all.stream().filter(t -> "待刃磨".equals(t.getStatus())).count());
        res.put("avgLifeRatio", all.isEmpty() ? 0 : Math.round(all.stream()
                .mapToDouble(t -> t.getLifeRatio() == null ? 0 : t.getLifeRatio().doubleValue()).average().orElse(0) * 10) / 10.0);
        res.put("byStatus", byStatus);
        res.put("byType", byType);
        res.put("warning", warning);
        res.put("all", all);
        res.put("valueByMachine", valueByMachine);
        return R.ok(res);
    }

    /** 装刀：绑定机台与刀补号，开启一次使用记录。 */
    @PostMapping("/mount")
    @Transactional
    public R<ToolUsage> mount(@RequestBody Map<String, Object> body) {
        Long toolId = Long.valueOf(String.valueOf(body.get("toolId")));
        Long machineId = Long.valueOf(String.valueOf(body.get("machineId")));
        ToolLedger t = mapper.selectById(toolId);
        if (t == null) throw new BizException("刀具不存在");
        MdMachine m = machineMapper.selectById(machineId);
        if (m == null) throw new BizException("机床不存在");
        if (t.getLifeRatio() != null && t.getLifeRatio().doubleValue() >= 100) {
            throw new BizException("该刀具寿命已耗尽（" + t.getLifeRatio() + "%），必须先刃磨或更换，禁止装刀");
        }
        String offsetNo = body.get("offsetNo") == null ? t.getOffsetNo() : String.valueOf(body.get("offsetNo"));
        t.setMachineId(machineId);
        t.setMachineCode(m.getMacCode());
        t.setOffsetNo(offsetNo);
        t.setStatus("在用");
        mapper.updateById(t);

        ToolUsage u = new ToolUsage();
        u.setToolId(t.getId());
        u.setToolCode(t.getToolCode());
        u.setToolName(t.getToolName());
        u.setMachineId(machineId);
        u.setMachineCode(m.getMacCode());
        u.setOffsetNo(offsetNo);
        u.setMountTime(LocalDateTime.now());
        u.setOperator(UserContext.realName());
        usageMapper.insert(u);
        return R.ok("装刀完成，刀补号 " + offsetNo, u);
    }

    /** 卸刀：累计本次加工时长与件数，按寿命自动落入「预警 / 待刃磨 / 在库」。 */
    @PostMapping("/unmount")
    @Transactional
    public R<ToolLedger> unmount(@RequestBody Map<String, Object> body) {
        Long toolId = Long.valueOf(String.valueOf(body.get("toolId")));
        ToolLedger t = mapper.selectById(toolId);
        if (t == null) throw new BizException("刀具不存在");
        ToolUsage open = usageMapper.selectOne(new QueryWrapper<ToolUsage>()
                .eq("tool_id", toolId).isNull("unmount_time").orderByDesc("mount_time").last("limit 1"));

        LocalDateTime now = LocalDateTime.now();
        double minutes = body.get("useMinutes") == null
                ? (open == null ? 0 : Duration.between(open.getMountTime(), now).toMinutes())
                : Double.parseDouble(String.valueOf(body.get("useMinutes")));
        int parts = body.get("partQty") == null ? 0 : Integer.parseInt(String.valueOf(body.get("partQty")));

        if (open != null) {
            open.setUnmountTime(now);
            open.setUseMinutes(BigDecimal.valueOf(minutes).setScale(2, RoundingMode.HALF_UP));
            open.setPartQty(parts);
            open.setOrderId(body.get("orderId") == null ? null : Long.valueOf(String.valueOf(body.get("orderId"))));
            open.setOrderNo(body.get("orderNo") == null ? null : String.valueOf(body.get("orderNo")));
            open.setNcProgramNo(body.get("ncProgramNo") == null ? null : String.valueOf(body.get("ncProgramNo")));
            open.setUnmountReason(body.get("unmountReason") == null ? "计划换刀" : String.valueOf(body.get("unmountReason")));
            open.setWearValue(body.get("wearValue") == null ? null : new BigDecimal(String.valueOf(body.get("wearValue"))));
            usageMapper.updateById(open);
        }

        t.setUsedLifeMin(BigDecimal.valueOf(
                (t.getUsedLifeMin() == null ? 0 : t.getUsedLifeMin().doubleValue()) + minutes).setScale(2, RoundingMode.HALF_UP));
        t.setUsedQty((t.getUsedQty() == null ? 0 : t.getUsedQty()) + parts);
        BigDecimal r = ratio(t);
        t.setLifeRatio(r);
        t.setMachineId(null);
        t.setMachineCode(null);
        if (r.doubleValue() >= 100) t.setStatus("待刃磨");
        else if (r.doubleValue() >= 85) t.setStatus("预警");
        else t.setStatus("在库");
        mapper.updateById(t);
        return R.ok("卸刀完成，当前寿命 " + r + "%（" + t.getStatus() + "）", t);
    }

    /** 刃磨登记：复位寿命计数器并记录刃磨量。 */
    @PostMapping("/grind")
    @Transactional
    public R<ToolLedger> grind(@RequestBody Map<String, Object> body) {
        Long toolId = Long.valueOf(String.valueOf(body.get("toolId")));
        ToolLedger t = mapper.selectById(toolId);
        if (t == null) throw new BizException("刀具不存在");

        ToolGrind g = new ToolGrind();
        g.setToolId(t.getId());
        g.setToolCode(t.getToolCode());
        g.setGrindNo(CodeGen.next("TG"));
        g.setGrindType(body.get("grindType") == null ? "刃磨" : String.valueOf(body.get("grindType")));
        g.setBeforeSpec(t.getSpec());
        g.setAfterSpec(body.get("afterSpec") == null ? t.getSpec() : String.valueOf(body.get("afterSpec")));
        g.setGrindAmount(body.get("grindAmount") == null ? null : new BigDecimal(String.valueOf(body.get("grindAmount"))));
        g.setGrindTime(LocalDateTime.now());
        g.setGrinder(UserContext.realName());
        g.setResult(body.get("result") == null ? "合格" : String.valueOf(body.get("result")));
        g.setCost(body.get("cost") == null ? null : new BigDecimal(String.valueOf(body.get("cost"))));
        g.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        grindMapper.insert(g);

        t.setUsedLifeMin(BigDecimal.ZERO);
        t.setUsedQty(0);
        t.setLifeRatio(BigDecimal.ZERO);
        t.setStatus("在库");
        mapper.updateById(t);
        return R.ok("刃磨登记完成，寿命已复位", t);
    }

    /** 对刀测量登记。 */
    @PostMapping("/measure")
    public R<ToolMeasure> measure(@RequestBody ToolMeasure m) {
        ToolLedger t = mapper.selectById(m.getToolId());
        if (t == null) throw new BizException("刀具不存在");
        m.setToolCode(t.getToolCode());
        m.setMeasureTime(LocalDateTime.now());
        if (m.getMeasurer() == null) m.setMeasurer(UserContext.realName());
        if (m.getResult() == null) m.setResult("合格");
        if (m.getMachineId() != null) {
            MdMachine mac = machineMapper.selectById(m.getMachineId());
            if (mac != null) m.setMachineCode(mac.getMacCode());
        }
        measureMapper.insert(m);
        return R.ok("对刀数据已登记", m);
    }

    /** 某把刀的使用记录。 */
    @GetMapping("/usage/{toolId}")
    public R<List<ToolUsage>> usage(@PathVariable Long toolId) {
        return R.ok(usageMapper.selectList(new QueryWrapper<ToolUsage>()
                .eq("tool_id", toolId).orderByDesc("mount_time")));
    }

    /** 刃磨记录。 */
    @GetMapping("/grindRecords/{toolId}")
    public R<List<ToolGrind>> grindRecords(@PathVariable Long toolId) {
        return R.ok(grindMapper.selectList(new QueryWrapper<ToolGrind>()
                .eq("tool_id", toolId).orderByDesc("grind_time")));
    }

    /** 对刀记录。 */
    @GetMapping("/measureRecords/{toolId}")
    public R<List<ToolMeasure>> measureRecords(@PathVariable Long toolId) {
        return R.ok(measureMapper.selectList(new QueryWrapper<ToolMeasure>()
                .eq("tool_id", toolId).orderByDesc("measure_time")));
    }

    /** 反向追溯：这把刀加工过哪些工件（序列号）与工单。 */
    @GetMapping("/trace/{toolCode}")
    public R<Map<String, Object>> trace(@PathVariable String toolCode) {
        List<TraceLog> logs = traceLogMapper.selectList(new QueryWrapper<TraceLog>()
                .eq("tool_code", toolCode).orderByDesc("start_time").last("limit 500"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("toolCode", toolCode);
        res.put("records", logs);
        res.put("snCount", logs.stream().map(TraceLog::getSnCode).distinct().count());
        res.put("orderCount", logs.stream().map(TraceLog::getOrderNo).distinct().count());
        res.put("orders", logs.stream().map(TraceLog::getOrderNo).distinct().toList());
        res.put("machines", logs.stream().map(TraceLog::getMachineCode).distinct().toList());
        return R.ok(res);
    }
}
