package com.mesmax.quality;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.BizException;
import com.mesmax.common.CodeGen;
import com.mesmax.common.R;
import com.mesmax.common.UserContext;
import com.mesmax.entity.QcDefect;
import com.mesmax.mapper.QcDefectMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 不合格品处置。
 *
 * <p>处置方式严格采用制造业通行的四态闭环（取自 WebErpMesv2 的 quality-derogations 设计）：
 * <b>返工 / 返修 / 让步接收 / 报废</b>。
 * 「让步接收」这一态最容易被简化掉，但它恰恰是质量成本核算的关键 —— 客户同意接收超差品时，
 * 需要有单据留痕，否则追溯时说不清楚。
 */
@RestController
@RequestMapping("/defect")
public class DefectController extends BaseCrudController<QcDefect> {

    private final QcDefectMapper mapper;

    public DefectController(QcDefectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<QcDefect> mapper() {
        return mapper;
    }

    @Override
    protected void beforeCreate(QcDefect entity) {
        if (entity.getDefectNo() == null || entity.getDefectNo().isBlank()) entity.setDefectNo(CodeGen.next("NC"));
        if (entity.getHandleStatus() == null) entity.setHandleStatus("待处理");
        if (entity.getHandleTime() == null && "已关闭".equals(entity.getHandleStatus())) {
            entity.setHandleTime(LocalDateTime.now());
        }
    }

    @Override
    protected void beforeUpdate(QcDefect entity) {
        if (entity.getHandleTime() == null && "已关闭".equals(entity.getHandleStatus())) {
            entity.setHandleTime(LocalDateTime.now());
        }
    }

    @Override
    protected String saveMessage() {
        return "不合格品单已保存";
    }

    /** 处置不合格品（返工/返修/让步接收/报废）。 */
    @PostMapping("/handle/{id}")
    public R<QcDefect> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        QcDefect d = mapper.selectById(id);
        if (d == null) throw new BizException("不合格品记录不存在");
        String handleType = body.get("handleType") == null ? d.getHandleType() : String.valueOf(body.get("handleType"));
        if (!List.of("返工", "返修", "让步接收", "报废").contains(handleType)) {
            throw new BizException("处置方式必须是：返工 / 返修 / 让步接收 / 报废");
        }
        d.setHandleType(handleType);
        d.setHandleStatus(body.get("handleStatus") == null ? "已关闭" : String.valueOf(body.get("handleStatus")));
        d.setHandler(UserContext.realName());
        d.setHandleTime(LocalDateTime.now());
        if (body.get("causeAnalysis") != null) d.setCauseAnalysis(String.valueOf(body.get("causeAnalysis")));
        if (body.get("costLoss") != null) d.setCostLoss(new BigDecimal(String.valueOf(body.get("costLoss"))));
        if (body.get("remark") != null) d.setRemark(String.valueOf(body.get("remark")));
        mapper.updateById(d);
        return R.ok("处置完成：" + handleType, d);
    }

    /** 不合格品统计：缺陷类型 TOP、责任原因分布、处置方式分布、质量损失。 */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(defaultValue = "14") int days) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<QcDefect> list = mapper.selectList(new QueryWrapper<QcDefect>().ge("create_time", from));

        Map<String, Double> byType = new LinkedHashMap<>();
        Map<String, Double> byReason = new LinkedHashMap<>();
        Map<String, Double> byHandle = new LinkedHashMap<>();
        Map<String, Double> byMachine = new LinkedHashMap<>();
        Map<String, Double> byDay = new TreeMap<>();
        double loss = 0;
        double qty = 0;

        for (QcDefect d : list) {
            double q = d.getDefectQty() == null ? 0 : d.getDefectQty().doubleValue();
            qty += q;
            loss += d.getCostLoss() == null ? 0 : d.getCostLoss().doubleValue();
            byType.merge(d.getDefectType() == null ? "其他" : d.getDefectType(), q, Double::sum);
            byReason.merge(d.getReasonType() == null ? "未分析" : d.getReasonType(), q, Double::sum);
            byHandle.merge(d.getHandleType() == null ? "未处置" : d.getHandleType(), q, Double::sum);
            byMachine.merge(d.getMachineCode() == null ? "-" : d.getMachineCode(), q, Double::sum);
            if (d.getCreateTime() != null) byDay.merge(d.getCreateTime().toLocalDate().toString(), q, Double::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", list.size());
        result.put("defectQty", qty);
        result.put("costLoss", Math.round(loss * 100) / 100.0);
        result.put("openCount", list.stream().filter(d -> !"已关闭".equals(d.getHandleStatus())).count());
        result.put("byType", toSeries(byType, "type"));
        result.put("byReason", toSeries(byReason, "reason"));
        result.put("byHandle", toSeries(byHandle, "handle"));
        result.put("byMachine", toSeries(byMachine, "machine"));
        result.put("byDay", toSeries(byDay, "day"));
        result.put("top", list.stream()
                .sorted((a, b) -> b.getDefectQty().compareTo(a.getDefectQty()))
                .limit(20).toList());
        return R.ok(result);
    }

    private List<Map<String, Object>> toSeries(Map<String, Double> map, String keyName) {
        List<Map<String, Object>> list = new ArrayList<>();
        map.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put(keyName, e.getKey());
                    m.put("qty", Math.round(e.getValue() * 100) / 100.0);
                    list.add(m);
                });
        return list;
    }
}
