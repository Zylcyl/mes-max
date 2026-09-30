package com.mesmax.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.common.BizException;
import com.mesmax.common.R;
import com.mesmax.entity.MdMachine;
import com.mesmax.entity.MdWorkCenter;
import com.mesmax.entity.ProdOrder;
import com.mesmax.entity.ProdOrderStep;
import com.mesmax.mapper.MdMachineMapper;
import com.mesmax.mapper.MdWorkCenterMapper;
import com.mesmax.mapper.ProdOrderMapper;
import com.mesmax.mapper.ProdOrderStepMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 生产排产：有限产能、机台负荷、甘特图。 */
@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    private final ProductionService productionService;
    private final ProdOrderMapper orderMapper;
    private final ProdOrderStepMapper stepMapper;
    private final MdMachineMapper machineMapper;
    private final MdWorkCenterMapper workCenterMapper;

    public ScheduleController(ProductionService productionService, ProdOrderMapper orderMapper,
                              ProdOrderStepMapper stepMapper, MdMachineMapper machineMapper,
                              MdWorkCenterMapper workCenterMapper) {
        this.productionService = productionService;
        this.orderMapper = orderMapper;
        this.stepMapper = stepMapper;
        this.machineMapper = machineMapper;
        this.workCenterMapper = workCenterMapper;
    }

    /** 机台负荷（默认今天起 7 天）。 */
    @GetMapping("/machineLoad")
    public R<List<Map<String, Object>>> machineLoad(@RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        LocalDateTime f = from == null ? LocalDate.now().atStartOfDay().minusDays(2) : LocalDateTime.parse(from);
        LocalDateTime t = to == null ? LocalDate.now().plusDays(7).atTime(23, 59, 59) : LocalDateTime.parse(to);
        return R.ok(productionService.machineLoad(f, t));
    }

    /** 甘特图数据。 */
    @GetMapping("/gantt")
    public R<List<Map<String, Object>>> gantt(@RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to,
                                              @RequestParam(required = false) Long machineId) {
        LocalDateTime f = from == null ? LocalDate.now().atStartOfDay().minusDays(3) : LocalDateTime.parse(from);
        LocalDateTime t = to == null ? LocalDate.now().plusDays(10).atTime(23, 59, 59) : LocalDateTime.parse(to);
        List<Map<String, Object>> list = productionService.gantt(f, t);
        if (machineId != null) list = list.stream().filter(m -> machineId.equals(m.get("machineId"))).toList();
        return R.ok(list);
    }

    /** 待排产任务。 */
    @GetMapping("/pending")
    public R<List<Map<String, Object>>> pending() {
        List<ProdOrderStep> steps = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                .in("status", List.of("PENDING", "DISPATCHED"))
                .orderByAsc("plan_start").last("limit 200"));
        Map<Long, ProdOrder> orders = orderMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProdOrder::getId, o -> o));
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProdOrderStep s : steps) {
            ProdOrder o = orders.get(s.getOrderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stepId", s.getId());
            m.put("orderId", s.getOrderId());
            m.put("orderNo", s.getOrderNo());
            m.put("productName", o == null ? null : o.getProductName());
            m.put("priority", o == null ? null : o.getPriority());
            m.put("priorityScore", o == null ? null : o.getPriorityScore());
            m.put("orderPlanEnd", o == null ? null : o.getPlanEnd());
            m.put("seqNo", s.getSeqNo());
            m.put("procName", s.getProcName());
            m.put("wcId", s.getWcId());
            m.put("wcName", s.getWcName());
            m.put("machineCode", s.getMachineCode());
            m.put("planQty", s.getPlanQty());
            m.put("setupTime", s.getSetupTime());
            m.put("pieceTime", s.getPieceTime());
            m.put("ncProgramNo", s.getNcProgramNo());
            m.put("planStart", s.getPlanStart());
            m.put("planEnd", s.getPlanEnd());
            m.put("status", s.getStatus());
            list.add(m);
        }
        return R.ok(list);
    }

    /** 一键自动排产（JobShop 启发式：加权优先级 + 最早空闲机台）。 */
    @PostMapping("/auto")
    public R<Map<String, Object>> auto(@RequestBody(required = false) Map<String, Object> body) {
        LocalDateTime from = null;
        List<Long> orderIds = null;
        if (body != null) {
            Object f = body.get("from");
            if (f != null && !String.valueOf(f).isBlank()) from = LocalDateTime.parse(String.valueOf(f));
            Object ids = body.get("orderIds");
            if (ids instanceof List<?> l) orderIds = l.stream().map(x -> Long.valueOf(String.valueOf(x))).toList();
        }
        return R.ok("排产完成", productionService.autoSchedule(from, orderIds));
    }

    /** 手动指派：把工序任务指定到机台与时间段（插单 / 调整）。 */
    @PostMapping("/assign")
    public R<ProdOrderStep> assign(@RequestBody Map<String, Object> body) {
        Long stepId = Long.valueOf(String.valueOf(body.get("stepId")));
        ProdOrderStep step = stepMapper.selectById(stepId);
        if (step == null) throw new BizException("工序任务不存在");
        if (body.get("machineId") != null && !String.valueOf(body.get("machineId")).isBlank()) {
            MdMachine m = machineMapper.selectById(Long.valueOf(String.valueOf(body.get("machineId"))));
            if (m == null) throw new BizException("机床不存在");
            step.setMachineId(m.getId());
            step.setMachineCode(m.getMacCode());
        }
        if (body.get("planStart") != null) step.setPlanStart(LocalDateTime.parse(String.valueOf(body.get("planStart"))));
        if (body.get("planEnd") != null) step.setPlanEnd(LocalDateTime.parse(String.valueOf(body.get("planEnd"))));
        if ("PENDING".equals(step.getStatus())) step.setStatus("DISPATCHED");
        stepMapper.updateById(step);
        return R.ok("排产已调整", step);
    }

    /** 排产资源：机台 + 工作中心（甘特图左侧泳道）。 */
    @GetMapping("/resources")
    public R<Map<String, Object>> resources() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("machines", machineMapper.selectList(new QueryWrapper<MdMachine>()
                .orderByAsc("work_center_id").orderByAsc("id")));
        m.put("workCenters", workCenterMapper.selectList(new QueryWrapper<MdWorkCenter>().orderByAsc("id")));
        return R.ok(m);
    }
}
