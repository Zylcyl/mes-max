package com.mesmax.quality;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.BizException;
import com.mesmax.common.CodeGen;
import com.mesmax.common.R;
import com.mesmax.common.UserContext;
import com.mesmax.entity.MdMachine;
import com.mesmax.entity.ProdOrderStep;
import com.mesmax.entity.QcInspection;
import com.mesmax.entity.QcInspectionItem;
import com.mesmax.mapper.MdMachineMapper;
import com.mesmax.mapper.ProdOrderStepMapper;
import com.mesmax.mapper.QcInspectionItemMapper;
import com.mesmax.mapper.QcInspectionMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量检验：首件 / 巡检 / 终检。
 *
 * <p>CNC 多品种小批量场景下，「首件检验」是最有价值的一道防线：工艺路线上的关键工序
 * （is_key_proc=1）在批量加工前必须先出首件结论，否则一旦程序或刀补有误就是整批报废。
 */
@RestController
@RequestMapping("/qc")
public class QualityController extends BaseCrudController<QcInspection> {

    private final QcInspectionMapper mapper;
    private final QcInspectionItemMapper itemMapper;
    private final ProdOrderStepMapper stepMapper;
    private final MdMachineMapper machineMapper;

    public QualityController(QcInspectionMapper mapper, QcInspectionItemMapper itemMapper,
                             ProdOrderStepMapper stepMapper, MdMachineMapper machineMapper) {
        this.mapper = mapper;
        this.itemMapper = itemMapper;
        this.stepMapper = stepMapper;
        this.machineMapper = machineMapper;
    }

    @Override
    protected BaseMapper<QcInspection> mapper() {
        return mapper;
    }

    @Override
    protected void beforeCreate(QcInspection entity) {
        if (entity.getInspNo() == null || entity.getInspNo().isBlank()) entity.setInspNo(CodeGen.next("QC"));
        if (entity.getInspTime() == null) entity.setInspTime(LocalDateTime.now());
        if (entity.getInspector() == null) entity.setInspector(UserContext.realName());
    }

    @Override
    protected void beforeUpdate(QcInspection entity) {
        // 更新仅在字段缺失时补齐，不覆盖已有值
        if (entity.getInspTime() == null) entity.setInspTime(LocalDateTime.now());
    }

    @Override
    protected String saveMessage() {
        return "检验单已保存";
    }

    /** 检验项明细。 */
    @GetMapping("/items/{inspectionId}")
    public R<List<QcInspectionItem>> items(@PathVariable Long inspectionId) {
        return R.ok(itemMapper.selectList(new QueryWrapper<QcInspectionItem>()
                .eq("inspection_id", inspectionId).orderByAsc("id")));
    }

    /** 保存检验项并自动判定。 */
    @PostMapping("/item/save")
    public R<QcInspectionItem> saveItem(@RequestBody QcInspectionItem item) {
        if (item.getMeasureValue() != null && item.getNominalValue() != null) {
            double v = item.getMeasureValue().doubleValue();
            double nominal = item.getNominalValue().doubleValue();
            double up = nominal + (item.getUpperTol() == null ? 0 : item.getUpperTol().doubleValue());
            double low = nominal + (item.getLowerTol() == null ? 0 : item.getLowerTol().doubleValue());
            item.setResult(v <= up && v >= low ? "PASS" : "FAIL");
        }
        if (item.getId() == null) itemMapper.insert(item);
        else itemMapper.updateById(item);
        return R.ok("已保存", item);
    }

    @DeleteMapping("/item/delete/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        itemMapper.deleteById(id);
        return R.ok("删除成功", null);
    }

    /** 一键创建首件检验单（带出该工序的常用检验项模板）。 */
    @PostMapping("/firstArticle/{stepId}")
    @Transactional
    public R<QcInspection> firstArticle(@PathVariable Long stepId, @RequestParam(required = false) String snCode) {
        ProdOrderStep step = stepMapper.selectById(stepId);
        if (step == null) throw new BizException("工序任务不存在");
        MdMachine machine = step.getMachineId() == null ? null : machineMapper.selectById(step.getMachineId());

        QcInspection ins = new QcInspection();
        ins.setInspNo(CodeGen.next("QC"));
        ins.setInspType("FIRST");
        ins.setOrderId(step.getOrderId());
        ins.setOrderNo(step.getOrderNo());
        ins.setStepId(step.getId());
        ins.setProcName(step.getProcName());
        ins.setMachineId(step.getMachineId());
        ins.setMachineCode(step.getMachineCode());
        ins.setSnCode(snCode);
        ins.setSampleQty(1);
        ins.setQualifiedQty(0);
        ins.setDefectQty(0);
        ins.setResult("PENDING");
        ins.setInspector(UserContext.realName());
        ins.setInspTime(LocalDateTime.now());
        ins.setConclusion("首件检验进行中");
        mapper.insert(ins);

        for (String[] def : DEFAULT_ITEMS) {
            QcInspectionItem it = new QcInspectionItem();
            it.setInspectionId(ins.getId());
            it.setItemName(def[0]);
            it.setItemType(def[1]);
            it.setNominalValue(new BigDecimal(def[2]));
            it.setUpperTol(new BigDecimal(def[3]));
            it.setLowerTol(new BigDecimal(def[3]).negate());
            it.setUnit(def[4]);
            it.setGaugeUsed(def[5]);
            it.setResult("PASS");
            itemMapper.insert(it);
        }
        return R.ok("首件检验单已创建（机床：" + (machine == null ? "未分配" : machine.getMacName()) + "）", ins);
    }

    private static final String[][] DEFAULT_ITEMS = {
            {"外圆直径", "DIM", "45.0000", "0.0250", "mm", "数显千分尺 0-25"},
            {"总长", "DIM", "120.0000", "0.1000", "mm", "游标卡尺 0-150"},
            {"端面跳动", "GEO", "0.0000", "0.0200", "mm", "三坐标测量机"},
            {"表面粗糙度 Ra", "SURFACE", "1.6000", "0.4000", "μm", "粗糙度仪"},
    };

    /** 提交判定：根据检验项结果汇总检验单结论。 */
    @PostMapping("/judge/{id}")
    public R<QcInspection> judge(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        QcInspection ins = mapper.selectById(id);
        if (ins == null) throw new BizException("检验单不存在");
        List<QcInspectionItem> items = itemMapper.selectList(new QueryWrapper<QcInspectionItem>()
                .eq("inspection_id", id));
        long fail = items.stream().filter(i -> "FAIL".equals(i.getResult())).count();
        ins.setResult(fail == 0 ? "PASS" : "FAIL");
        ins.setDefectQty((int) fail);
        ins.setQualifiedQty(Math.max(0, (ins.getSampleQty() == null ? items.size() : ins.getSampleQty()) - (int) fail));
        ins.setConclusion(fail == 0
                ? "全部检验项合格，判定合格，允许继续加工"
                : "存在 " + fail + " 项超差，判定不合格，须停线处理并开具不合格品处置单");
        if (body != null && body.get("conclusion") != null) ins.setConclusion(String.valueOf(body.get("conclusion")));
        mapper.updateById(ins);

        // 首件合格 → 工序任务从 INSPECTING 回到 RUNNING，允许批量加工
        if ("PASS".equals(ins.getResult()) && "FIRST".equals(ins.getInspType()) && ins.getStepId() != null) {
            ProdOrderStep step = stepMapper.selectById(ins.getStepId());
            if (step != null && "INSPECTING".equals(step.getStatus())) {
                step.setStatus("RUNNING");
                stepMapper.updateById(step);
            }
        }
        return R.ok("判定完成：" + ins.getResult(), ins);
    }

    /** 检验合格率统计（按类型、按机床、按天）。 */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(defaultValue = "14") int days) {
        int span = Math.max(1, Math.min(days, 365));
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        List<QcInspection> list = mapper.selectList(new QueryWrapper<QcInspection>()
                .ge("insp_time", from).le("insp_time", to).orderByAsc("insp_time"));

        Map<String, int[]> byType = new LinkedHashMap<>();
        Map<String, int[]> byMachine = new LinkedHashMap<>();
        Map<String, int[]> byDay = new java.util.TreeMap<>();
        for (QcInspection i : list) {
            int pass = "PASS".equals(i.getResult()) ? 1 : 0;
            String t = switch (i.getInspType() == null ? "" : i.getInspType()) {
                case "FIRST" -> "首件检验";
                case "PATROL" -> "巡检";
                case "FINAL" -> "终检";
                default -> "其他";
            };
            byType.computeIfAbsent(t, k -> new int[2]);
            byType.get(t)[0] += pass;
            byType.get(t)[1] += 1;
            byMachine.computeIfAbsent(i.getMachineCode() == null ? "-" : i.getMachineCode(), k -> new int[2]);
            byMachine.get(i.getMachineCode() == null ? "-" : i.getMachineCode())[0] += pass;
            byMachine.get(i.getMachineCode() == null ? "-" : i.getMachineCode())[1] += 1;
            if (i.getInspTime() != null) {
                String d = i.getInspTime().toLocalDate().toString();
                byDay.computeIfAbsent(d, k -> new int[2]);
                byDay.get(d)[0] += pass;
                byDay.get(d)[1] += 1;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", span);
        result.put("total", list.size());
        result.put("passCount", list.stream().filter(i -> "PASS".equals(i.getResult())).count());
        result.put("failCount", list.stream().filter(i -> "FAIL".equals(i.getResult())).count());
        double pass = list.stream().filter(i -> "PASS".equals(i.getResult())).count();
        result.put("passRate", list.isEmpty() ? 0 : Math.round(pass / list.size() * 1000) / 10.0);
        result.put("byType", ratio(byType, "type"));
        result.put("byMachine", ratio(byMachine, "machine"));
        result.put("byDay", ratio(byDay, "day"));
        return R.ok(result);
    }

    private List<Map<String, Object>> ratio(Map<String, int[]> map, String keyName) {
        List<Map<String, Object>> list = new ArrayList<>();
        map.forEach((k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(keyName, k);
            m.put("pass", v[0]);
            m.put("total", v[1]);
            m.put("rate", v[1] == 0 ? 0 : Math.round(v[0] * 1000.0 / v[1]) / 10.0);
            list.add(m);
        });
        return list;
    }
}
