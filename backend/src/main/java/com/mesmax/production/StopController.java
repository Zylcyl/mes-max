package com.mesmax.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.BizException;
import com.mesmax.common.R;
import com.mesmax.entity.MdMachine;
import com.mesmax.entity.ProdStopRecord;
import com.mesmax.mapper.MdMachineMapper;
import com.mesmax.mapper.ProdStopRecordMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 停机管理：停机明细 + 原因帕累托分析（OEE 时间损失改进的依据）。 */
@RestController
@RequestMapping("/stop")
public class StopController extends BaseCrudController<ProdStopRecord> {

    private static final Map<String, String> REASON_LABEL = Map.of(
            "TOOL_CHANGE", "换刀",
            "CHANGEOVER", "换型调机",
            "FAULT", "设备故障",
            "WAIT_MATERIAL", "等待毛坯",
            "WAIT_PROGRAM", "等待程序",
            "WAIT_INSPECT", "首件检验",
            "PLAN_MAINT", "计划保养",
            "IDLE", "待料停工");

    private final ProdStopRecordMapper mapper;
    private final MdMachineMapper machineMapper;
    private final ProductionService productionService;

    public StopController(ProdStopRecordMapper mapper, MdMachineMapper machineMapper,
                          ProductionService productionService) {
        this.mapper = mapper;
        this.machineMapper = machineMapper;
        this.productionService = productionService;
    }

    @Override
    protected BaseMapper<ProdStopRecord> mapper() {
        return mapper;
    }

    @Override
    protected void beforeCreate(ProdStopRecord entity) {
        productionService.fillStop(entity);
    }

    @Override
    protected void beforeUpdate(ProdStopRecord entity) {
        // 更新时只在两个时间都在场的情况下重算时长，避免部分字段更新把时长清零
        if (entity.getStartTime() != null && entity.getEndTime() != null) {
            productionService.fillStop(entity);
        }
    }

    @Override
    protected String saveMessage() {
        return "停机记录已保存";
    }

    /** 停机统计：按原因（帕累托）、按类型、按机床、按天。 */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(defaultValue = "7") int days) {
        int span = Math.max(1, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(span - 1L);
        List<ProdStopRecord> list = mapper.selectList(new QueryWrapper<ProdStopRecord>()
                .ge("start_time", from.atStartOfDay())
                .lt("start_time", to.plusDays(1).atStartOfDay())
                .orderByAsc("start_time"));

        Map<String, Double> byReason = new LinkedHashMap<>();
        Map<String, Double> byType = new LinkedHashMap<>();
        Map<String, Double> byMachine = new LinkedHashMap<>();
        Map<String, Double> byDay = new TreeMap<>();
        double total = 0;

        for (ProdStopRecord s : list) {
            double d = s.getDurationMin() == null ? 0 : s.getDurationMin().doubleValue();
            total += d;
            String reason = REASON_LABEL.getOrDefault(s.getReasonCode(),
                    s.getReasonCode() == null ? "其他" : s.getReasonCode());
            byReason.merge(reason, d, Double::sum);
            byType.merge(s.getStopType() == null ? "未分类" : s.getStopType(), d, Double::sum);
            byMachine.merge(s.getMachineCode() == null ? "-" : s.getMachineCode(), d, Double::sum);
            if (s.getStartTime() != null) byDay.merge(s.getStartTime().toLocalDate().toString(), d, Double::sum);
        }

        final double finalTotal = total;
        List<Map<String, Object>> reasonPareto = new ArrayList<>();
        byReason.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("reason", e.getKey());
                    m.put("minutes", Math.round(e.getValue()));
                    m.put("hours", Math.round(e.getValue() / 6.0) / 10.0);
                    m.put("ratio", finalTotal == 0 ? 0 : Math.round(e.getValue() / finalTotal * 1000) / 10.0);
                    reasonPareto.add(m);
                });
        double cum = 0;
        for (Map<String, Object> m : reasonPareto) {
            cum += (double) m.get("ratio");
            m.put("cumulative", Math.round(cum * 10) / 10.0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", span);
        result.put("totalMinutes", Math.round(total));
        result.put("totalHours", Math.round(total / 6.0) / 10.0);
        result.put("count", list.size());
        result.put("byReason", reasonPareto);
        result.put("byType", toSeries(byType, "type"));
        result.put("byMachine", toSeries(byMachine, "machine"));
        result.put("byDay", toSeries(byDay, "day"));
        result.put("machines", machineMapper.selectList(new QueryWrapper<MdMachine>().orderByAsc("id")));
        return R.ok(result);
    }

    private List<Map<String, Object>> toSeries(Map<String, Double> map, String keyName) {
        List<Map<String, Object>> list = new ArrayList<>();
        map.forEach((k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(keyName, k);
            m.put("minutes", Math.round(v));
            m.put("hours", Math.round(v / 6.0) / 10.0);
            list.add(m);
        });
        return list;
    }

    /** 当前未结束的停机。 */
    @GetMapping("/ongoing")
    public R<List<ProdStopRecord>> ongoing() {
        return R.ok(mapper.selectList(new QueryWrapper<ProdStopRecord>()
                .isNull("end_time").orderByDesc("start_time")));
    }

    /** 结束一条停机记录。 */
    @PostMapping("/close/{id}")
    public R<ProdStopRecord> close(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProdStopRecord s = mapper.selectById(id);
        if (s == null) throw new BizException("停机记录不存在");
        s.setEndTime(LocalDateTime.now());
        if (body != null && body.get("remark") != null) s.setRemark(String.valueOf(body.get("remark")));
        s.setStatus("已关闭");
        if (s.getStartTime() != null) {
            s.setDurationMin(BigDecimal.valueOf(Duration.between(s.getStartTime(), s.getEndTime()).toMinutes()));
        }
        mapper.updateById(s);
        return R.ok("停机已结束", s);
    }
}
