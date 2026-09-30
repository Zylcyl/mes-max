package com.mesmax.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.BizException;
import com.mesmax.common.CodeGen;
import com.mesmax.common.R;
import com.mesmax.entity.MdMaterial;
import com.mesmax.entity.MdRouting;
import com.mesmax.entity.ProdDispatch;
import com.mesmax.entity.ProdOrder;
import com.mesmax.entity.ProdOrderStep;
import com.mesmax.mapper.MdMaterialMapper;
import com.mesmax.mapper.MdRoutingMapper;
import com.mesmax.mapper.ProdDispatchMapper;
import com.mesmax.mapper.ProdOrderMapper;
import com.mesmax.mapper.ProdOrderStepMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 生产订单（车间工单）：下达、展开工序、齐套检查、执行跟踪。 */
@RestController
@RequestMapping("/order")
public class OrderController extends BaseCrudController<ProdOrder> {

    private final ProdOrderMapper mapper;
    private final ProdOrderStepMapper stepMapper;
    private final ProdDispatchMapper dispatchMapper;
    private final MdMaterialMapper materialMapper;
    private final MdRoutingMapper routingMapper;
    private final ProductionService productionService;

    public OrderController(ProdOrderMapper mapper, ProdOrderStepMapper stepMapper, ProdDispatchMapper dispatchMapper,
                           MdMaterialMapper materialMapper, MdRoutingMapper routingMapper,
                           ProductionService productionService) {
        this.mapper = mapper;
        this.stepMapper = stepMapper;
        this.dispatchMapper = dispatchMapper;
        this.materialMapper = materialMapper;
        this.routingMapper = routingMapper;
        this.productionService = productionService;
    }

    @Override
    protected BaseMapper<ProdOrder> mapper() {
        return mapper;
    }

    @Override
    protected String saveMessage() {
        return "工单已保存";
    }

    /** 新建订单：生成单号、按产品带出工艺路线、补齐默认值与优先级评分。 */
    @Override
    protected void beforeCreate(ProdOrder order) {
        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            order.setOrderNo(CodeGen.next("WO"));
        }
        fillProduct(order);
        if (order.getStatus() == null) order.setStatus("RELEASED");
        if (order.getFinishedQty() == null) order.setFinishedQty(BigDecimal.ZERO);
        if (order.getQualifiedQty() == null) order.setQualifiedQty(BigDecimal.ZERO);
        if (order.getDefectQty() == null) order.setDefectQty(BigDecimal.ZERO);
        if (order.getPlanStart() == null) order.setPlanStart(LocalDateTime.now().plusHours(2));
        if (order.getPlanEnd() == null) order.setPlanEnd(order.getPlanStart().plusDays(2));
        if (order.getPriorityScore() == null) order.setPriorityScore(scoreOf(order.getPriority()));
    }

    /**
     * 更新订单：请求体通常只带改动过的字段，
     * 因此只重算「由本次入参可推导」的字段（产品快照、优先级评分），
     * 不去动状态、完工数量与计划时间 —— 否则一次局部编辑就会把生产进度清零。
     */
    @Override
    protected void beforeUpdate(ProdOrder order) {
        fillProduct(order);
        if (order.getPriority() != null) order.setPriorityScore(scoreOf(order.getPriority()));
    }

    /** 依据产品物料带出产品编码/名称/规格，并在未指定时自动匹配工艺路线。 */
    private void fillProduct(ProdOrder order) {
        if (order.getProductMatId() == null) return;
        MdMaterial mat = materialMapper.selectById(order.getProductMatId());
        if (mat != null) {
            order.setProductCode(mat.getMatCode());
            order.setProductName(mat.getMatName());
            order.setProductSpec(mat.getSpec());
        }
        if (order.getRoutingId() == null) {
            MdRouting r = routingMapper.selectOne(new QueryWrapper<MdRouting>()
                    .eq("product_mat_id", order.getProductMatId()).last("limit 1"));
            if (r != null) order.setRoutingId(r.getId());
        }
    }

    /** JobShop 加权优先级评分：优先级 + 客户等级 + 订单类型（与演示数据生成器口径一致）。 */
    private int scoreOf(String priority) {
        int score = 50;
        if ("加急".equals(priority)) score += 35;
        else if ("高".equals(priority)) score += 22;
        else if ("中".equals(priority)) score += 10;
        return score;
    }

    /** 工单详情：订单 + 工序任务 + 派工单。 */
    @GetMapping("/detail/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        ProdOrder order = mapper.selectById(id);
        if (order == null) throw new BizException("订单不存在");
        List<ProdOrderStep> steps = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                .eq("order_id", id).orderByAsc("seq_no"));
        List<ProdDispatch> dispatches = dispatchMapper.selectList(new QueryWrapper<ProdDispatch>()
                .eq("order_id", id).orderByAsc("seq_no"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", order);
        m.put("steps", steps);
        m.put("dispatches", dispatches);
        m.put("progress", order.getPlanQty() == null || order.getPlanQty().doubleValue() == 0 ? 0
                : Math.min(100, Math.round(order.getFinishedQty().doubleValue() / order.getPlanQty().doubleValue() * 100)));
        return R.ok(m);
    }

    /** 下达工单并展开工序任务。 */
    @PostMapping("/release/{id}")
    public R<Map<String, Object>> release(@PathVariable Long id) {
        return R.ok("工单已下达", productionService.releaseOrder(id));
    }

    /** 仅展开工序任务（不改变订单状态）。 */
    @PostMapping("/expand/{id}")
    public R<Map<String, Object>> expand(@PathVariable Long id) {
        ProdOrder order = mapper.selectById(id);
        if (order == null) throw new BizException("订单不存在");
        Long c = stepMapper.selectCount(new QueryWrapper<ProdOrderStep>().eq("order_id", id));
        if (c != null && c > 0) throw new BizException("该工单已展开工序任务，如需重排请先删除原任务");
        int n = productionService.expandSteps(order);
        return R.ok("已展开 " + n + " 道工序", Map.of("created", n));
    }

    /** 齐套检查：按 BOM 折算毛坯与标准件需求。 */
    @GetMapping("/kitCheck/{id}")
    public R<Map<String, Object>> kitCheck(@PathVariable Long id) {
        return R.ok(productionService.kitCheck(id));
    }

    /** 批量齐套检查。 */
    @PostMapping("/kitCheck/batch")
    public R<List<Map<String, Object>>> kitCheckBatch(@RequestBody List<Long> ids) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Long id : ids) {
            try {
                list.add(productionService.kitCheck(id));
            } catch (Exception ignored) {
                // 单个订单异常不影响批量结果
            }
        }
        return R.ok(list);
    }

    /** 变更工单状态（挂起 / 恢复 / 取消）。 */
    @PostMapping("/status/{id}")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        ProdOrder order = mapper.selectById(id);
        if (order == null) throw new BizException("订单不存在");
        order.setStatus(status);
        mapper.updateById(order);
        return R.ok("状态已更新为 " + status, null);
    }

    /** 订单列表（含工序进度摘要）。 */
    @GetMapping("/withProgress")
    public R<List<Map<String, Object>>> withProgress(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "50") int size) {
        QueryWrapper<ProdOrder> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByDesc("priority_score").orderByDesc("id").last("limit " + Math.min(size, 300));
        List<ProdOrder> orders = mapper.selectList(qw);
        Map<Long, List<ProdOrderStep>> stepMap = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                        .in("order_id", orders.isEmpty() ? List.of(-1L) : orders.stream().map(ProdOrder::getId).toList()))
                .stream().collect(Collectors.groupingBy(ProdOrderStep::getOrderId));

        List<Map<String, Object>> list = new ArrayList<>();
        for (ProdOrder o : orders) {
            List<ProdOrderStep> steps = stepMap.getOrDefault(o.getId(), List.of());
            long done = steps.stream().filter(s -> "FINISHED".equals(s.getStatus())).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("customerName", o.getCustomerName());
            m.put("productName", o.getProductName());
            m.put("productCode", o.getProductCode());
            m.put("planQty", o.getPlanQty());
            m.put("finishedQty", o.getFinishedQty());
            m.put("qualifiedQty", o.getQualifiedQty());
            m.put("defectQty", o.getDefectQty());
            m.put("planStart", o.getPlanStart());
            m.put("planEnd", o.getPlanEnd());
            m.put("priority", o.getPriority());
            m.put("priorityScore", o.getPriorityScore());
            m.put("orderType", o.getOrderType());
            m.put("status", o.getStatus());
            m.put("stepCount", steps.size());
            m.put("stepDone", done);
            m.put("currentProc", steps.stream()
                    .filter(s -> "RUNNING".equals(s.getStatus()) || "INSPECTING".equals(s.getStatus()))
                    .map(ProdOrderStep::getProcName).findFirst().orElse(null));
            /*
             * 列表里的「进度」用工序完成度表达。
             * 数量口径下 order.finished_qty 已改为「最后一道工序产出（成品数）」，
             * 在制工单会长期停在 0%，配合工序完成度才能如实反映推进情况；
             * 数量口径另以 qtyProgress 单独给出。
             */
            double plan = o.getPlanQty() == null ? 0 : o.getPlanQty().doubleValue();
            m.put("progress", steps.isEmpty() ? 0 : Math.round(done * 100.0 / steps.size()));
            m.put("qtyProgress", plan == 0 ? 0 : Math.min(100, Math.round(o.getFinishedQty().doubleValue() / plan * 100)));
            list.add(m);
        }
        return R.ok(list);
    }
}
