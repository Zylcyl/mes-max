package com.mesmax.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.common.BizException;
import com.mesmax.common.CodeGen;
import com.mesmax.entity.*;
import com.mesmax.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生产执行核心服务。
 *
 * <p>集中承载四件事，避免业务规则散落到各个 Controller：
 * <ol>
 *   <li><b>工单展开</b>：按工艺路线生成工序任务，并把定额工时与 NC 程序号「快照」下来 ——
 *       后续修改工艺路线不会影响已在制的工单（这一设计取自 industry4.0-mes 的三层定额体系）。</li>
 *   <li><b>有限产能排产</b>：JobShop 模型，按「机台队列 + 加权优先级插单」排产。</li>
 *   <li><b>报工闭环</b>：报工 → 工序任务 → 订单 数量三级联动，并同步写追溯记录。</li>
 *   <li><b>齐套校验</b>：按 BOM 折算毛坯与标准件需求，对比库存给出齐套结论。</li>
 * </ol>
 */
@Service
public class ProductionService {

    private final ProdOrderMapper orderMapper;
    private final ProdOrderStepMapper stepMapper;
    private final ProdDispatchMapper dispatchMapper;
    private final ProdTaskReportMapper reportMapper;
    private final ProdMachineStatusLogMapper statusLogMapper;
    private final ProdStopRecordMapper stopMapper;
    private final MdRoutingMapper routingMapper;
    private final MdRoutingStepMapper routingStepMapper;
    private final MdBomMapper bomMapper;
    private final MdBomItemMapper bomItemMapper;
    private final MdMaterialMapper materialMapper;
    private final MdMachineMapper machineMapper;
    private final MdEmployeeMapper employeeMapper;
    private final MdShiftMapper shiftMapper;
    private final TraceSnMapper snMapper;
    private final TraceLogMapper traceLogMapper;
    private final ToolLedgerMapper toolMapper;

    public ProductionService(ProdOrderMapper orderMapper, ProdOrderStepMapper stepMapper,
                             ProdDispatchMapper dispatchMapper, ProdTaskReportMapper reportMapper,
                             ProdMachineStatusLogMapper statusLogMapper, ProdStopRecordMapper stopMapper,
                             MdRoutingMapper routingMapper, MdRoutingStepMapper routingStepMapper,
                             MdBomMapper bomMapper, MdBomItemMapper bomItemMapper,
                             MdMaterialMapper materialMapper, MdMachineMapper machineMapper,
                             MdEmployeeMapper employeeMapper, MdShiftMapper shiftMapper,
                             TraceSnMapper snMapper, TraceLogMapper traceLogMapper,
                             ToolLedgerMapper toolMapper) {
        this.orderMapper = orderMapper;
        this.stepMapper = stepMapper;
        this.dispatchMapper = dispatchMapper;
        this.reportMapper = reportMapper;
        this.statusLogMapper = statusLogMapper;
        this.stopMapper = stopMapper;
        this.routingMapper = routingMapper;
        this.routingStepMapper = routingStepMapper;
        this.bomMapper = bomMapper;
        this.bomItemMapper = bomItemMapper;
        this.materialMapper = materialMapper;
        this.machineMapper = machineMapper;
        this.employeeMapper = employeeMapper;
        this.shiftMapper = shiftMapper;
        this.snMapper = snMapper;
        this.traceLogMapper = traceLogMapper;
        this.toolMapper = toolMapper;
    }

    // ==================================================================
    //  1. 工单展开
    // ==================================================================

    /** 下达工单：状态置 RELEASED，并按工艺路线展开工序任务（幂等：已展开则跳过）。 */
    @Transactional
    public Map<String, Object> releaseOrder(Long orderId) {
        ProdOrder order = requireOrder(orderId);
        Long exists = stepMapper.selectCount(new QueryWrapper<ProdOrderStep>().eq("order_id", orderId));
        int created = 0;
        if (exists == null || exists == 0) {
            created = expandSteps(order);
        }
        if (!"FINISHED".equals(order.getStatus()) && !"CANCELED".equals(order.getStatus())) {
            order.setStatus("RELEASED");
            orderMapper.updateById(order);
        }
        return Map.of("orderNo", order.getOrderNo(), "createdSteps", created);
    }

    /** 按工艺路线展开工序任务；定额与程序号在此快照。 */
    @Transactional
    public int expandSteps(ProdOrder order) {
        if (order.getRoutingId() == null) throw new BizException("该订单未绑定工艺路线，无法展开工序");
        List<MdRoutingStep> route = routingStepMapper.selectList(new QueryWrapper<MdRoutingStep>()
                .eq("routing_id", order.getRoutingId()).orderByAsc("seq_no"));
        if (route.isEmpty()) throw new BizException("工艺路线下没有任何工序，请先维护工艺路线");

        LocalDateTime cursor = order.getPlanStart() == null ? LocalDateTime.now().plusHours(4) : order.getPlanStart();
        double qty = order.getPlanQty() == null ? 0 : order.getPlanQty().doubleValue();
        int n = 0;
        for (MdRoutingStep rs : route) {
            ProdOrderStep st = new ProdOrderStep();
            st.setOrderId(order.getId());
            st.setOrderNo(order.getOrderNo());
            st.setSeqNo(rs.getSeqNo());
            st.setProcId(rs.getProcId());
            st.setProcCode(rs.getProcCode());
            st.setProcName(rs.getProcName());
            st.setWcId(rs.getWcId());
            st.setWcName(rs.getWcName());
            st.setPlanQty(order.getPlanQty());
            st.setFinishedQty(BigDecimal.ZERO);
            st.setQualifiedQty(BigDecimal.ZERO);
            st.setDefectQty(BigDecimal.ZERO);
            st.setSetupTime(rs.getSetupTime());
            st.setPieceTime(rs.getPieceTime());
            st.setRunMinutes(BigDecimal.ZERO);
            st.setStopMinutes(BigDecimal.ZERO);
            st.setNcProgramNo(rs.getNcProgramNo());
            st.setNcProgramVer(rs.getNcProgramVer());
            st.setToolRequirement(rs.getToolRequirement());
            st.setIsKeyProc(rs.getIsKeyProc());
            st.setIsInspection(rs.getIsInspection());
            st.setPlanStart(cursor);
            double workMin = nz(rs.getSetupTime()) + nz(rs.getPieceTime()) * qty;
            st.setPlanEnd(cursor.plusMinutes(Math.max(45, Math.round(workMin))));
            st.setStatus("PENDING");
            cursor = st.getPlanEnd().plusMinutes(30);
            stepMapper.insert(st);
            n++;
        }
        order.setPlanEnd(cursor);
        orderMapper.updateById(order);
        return n;
    }

    // ==================================================================
    //  2. 有限产能排产（JobShop）
    // ==================================================================

    /**
     * 自动排产：以待排任务的加权优先级降序，逐个投放到「最早空闲的可用机台」上。
     *
     * <p>算法本质是 list scheduling（启发式），对机加工这种单件小批、机台可用性差异大的场景，
     * 比尝试求最优解的代价低得多，且结果稳定可解释 —— 这是车间计划员真正需要的。
     */
    @Transactional
    public Map<String, Object> autoSchedule(LocalDateTime from, List<Long> orderIds) {
        LocalDateTime start = from == null ? LocalDateTime.now().plusMinutes(30) : from;

        QueryWrapper<ProdOrderStep> qw = new QueryWrapper<ProdOrderStep>()
                .in("status", List.of("PENDING", "DISPATCHED"))
                .orderByAsc("plan_start");
        if (orderIds != null && !orderIds.isEmpty()) qw.in("order_id", orderIds);
        List<ProdOrderStep> tasks = stepMapper.selectList(qw);

        Map<Long, ProdOrder> orderMap = new HashMap<>();
        for (ProdOrder o : orderMapper.selectList(null)) orderMap.put(o.getId(), o);

        tasks.sort(Comparator
                .comparingInt((ProdOrderStep s) -> {
                    ProdOrder o = orderMap.get(s.getOrderId());
                    return o == null || o.getPriorityScore() == null ? 0 : -o.getPriorityScore();
                })
                .thenComparing(ProdOrderStep::getPlanStart, Comparator.nullsLast(Comparator.naturalOrder())));

        List<MdMachine> machines = machineMapper.selectList(null);
        Map<Long, List<MdMachine>> byWc = machines.stream()
                .collect(Collectors.groupingBy(MdMachine::getWorkCenterId));
        Map<Long, LocalDateTime> cursor = new HashMap<>();
        machines.forEach(m -> cursor.put(m.getId(), start));

        int scheduled = 0;
        int unassigned = 0;
        for (ProdOrderStep t : tasks) {
            List<MdMachine> candidates = byWc.getOrDefault(t.getWcId(), List.of());
            MdMachine target = candidates.stream()
                    .min(Comparator.comparing(m -> cursor.get(m.getId())))
                    .orElse(null);
            if (target == null) {
                unassigned++;
                continue;
            }
            double workMin = nz(t.getSetupTime()) + nz(t.getPieceTime()) * nz(t.getPlanQty());
            LocalDateTime begin = cursor.get(target.getId());
            t.setMachineId(target.getId());
            t.setMachineCode(target.getMacCode());
            t.setPlanStart(begin);
            t.setPlanEnd(begin.plusMinutes(Math.max(45, Math.round(workMin))));
            if ("PENDING".equals(t.getStatus())) t.setStatus("DISPATCHED");
            stepMapper.updateById(t);
            cursor.put(target.getId(), t.getPlanEnd().plusMinutes(20));
            scheduled++;
        }
        return Map.of("scheduled", scheduled, "unassigned", unassigned, "from", start.toString());
    }

    /** 机台负荷：返回 [机台 × 日] 的计划工时与已完工工时，用于负荷热力图。 */
    public List<Map<String, Object>> machineLoad(LocalDateTime from, LocalDateTime to) {
        List<ProdOrderStep> steps = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                .ge("plan_start", from).le("plan_start", to));
        List<MdMachine> machines = machineMapper.selectList(new QueryWrapper<MdMachine>().orderByAsc("id"));
        Map<Long, List<ProdOrderStep>> byMachine = steps.stream()
                .filter(s -> s.getMachineId() != null)
                .collect(Collectors.groupingBy(ProdOrderStep::getMachineId));

        List<Map<String, Object>> result = new ArrayList<>();
        long days = Math.max(1, Duration.between(from, to).toDays() + 1);
        double dailyCapacity = 450.0; // 单机单班有效工作时间（分钟）

        for (MdMachine m : machines) {
            List<ProdOrderStep> list = byMachine.getOrDefault(m.getId(), List.of());
            double planMin = list.stream().mapToDouble(s -> nz(s.getSetupTime()) + nz(s.getPieceTime()) * nz(s.getPlanQty())).sum();
            double doneMin = list.stream().mapToDouble(s -> nz(s.getRunMinutes())).sum();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("machineId", m.getId());
            item.put("machineCode", m.getMacCode());
            item.put("machineName", m.getMacName());
            item.put("cncType", m.getCncType());
            item.put("workCenterId", m.getWorkCenterId());
            item.put("workshopId", m.getWorkshopId());
            item.put("status", m.getStatus());
            item.put("taskCount", list.size());
            item.put("planMinutes", Math.round(planMin));
            item.put("doneMinutes", Math.round(doneMin));
            item.put("capacityMinutes", Math.round(dailyCapacity * days));
            double load = planMin / (dailyCapacity * days) * 100;
            item.put("loadRate", Math.round(load * 10) / 10.0);
            item.put("level", load > 95 ? "OVER" : load > 75 ? "HIGH" : load > 40 ? "NORMAL" : "LOW");
            result.add(item);
        }
        return result;
    }

    /** 甘特图数据：按机床分组的工序任务条。 */
    public List<Map<String, Object>> gantt(LocalDateTime from, LocalDateTime to) {
        List<ProdOrderStep> steps = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                .ge("plan_start", from).le("plan_start", to).orderByAsc("plan_start"));
        Map<Long, ProdOrder> orders = orderMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProdOrder::getId, o -> o));
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProdOrderStep s : steps) {
            ProdOrder o = orders.get(s.getOrderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("orderId", s.getOrderId());
            m.put("orderNo", s.getOrderNo());
            m.put("productName", o == null ? null : o.getProductName());
            m.put("priority", o == null ? null : o.getPriority());
            m.put("priorityScore", o == null ? null : o.getPriorityScore());
            m.put("seqNo", s.getSeqNo());
            m.put("procName", s.getProcName());
            m.put("machineId", s.getMachineId());
            m.put("machineCode", s.getMachineCode());
            m.put("wcName", s.getWcName());
            m.put("planStart", s.getPlanStart());
            m.put("planEnd", s.getPlanEnd());
            m.put("planQty", s.getPlanQty());
            m.put("finishedQty", s.getFinishedQty());
            m.put("status", s.getStatus());
            m.put("ncProgramNo", s.getNcProgramNo());
            double plan = nz(s.getPlanQty());
            m.put("progress", plan <= 0 ? 0 : Math.min(100, Math.round(nz(s.getFinishedQty()) / plan * 100)));
            list.add(m);
        }
        return list;
    }

    // ==================================================================
    //  3. 派工
    // ==================================================================

    @Transactional
    public ProdDispatch createDispatch(Long stepId, Long machineId, Long employeeId, Long shiftId, BigDecimal planQty) {
        ProdOrderStep step = stepMapper.selectById(stepId);
        if (step == null) throw new BizException("工序任务不存在");
        ProdOrder order = requireOrder(step.getOrderId());

        if (machineId != null) {
            MdMachine m = machineMapper.selectById(machineId);
            if (m == null) throw new BizException("机床不存在");
            step.setMachineId(m.getId());
            step.setMachineCode(m.getMacCode());
        }
        MdEmployee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        MdShift shift = shiftId == null ? null : shiftMapper.selectById(shiftId);

        ProdDispatch d = new ProdDispatch();
        d.setDispatchNo(CodeGen.next("DP"));
        d.setOrderId(order.getId());
        d.setOrderNo(order.getOrderNo());
        d.setStepId(step.getId());
        d.setSeqNo(step.getSeqNo());
        d.setProcName(step.getProcName());
        d.setMachineId(step.getMachineId());
        d.setMachineCode(step.getMachineCode());
        d.setEmployeeId(emp == null ? null : emp.getId());
        d.setEmployeeName(emp == null ? null : emp.getEmpName());
        d.setTeamName(emp == null ? null : emp.getTeamName());
        d.setShiftId(shift == null ? null : shift.getId());
        d.setShiftName(shift == null ? null : shift.getShiftName());
        d.setPlanQty(planQty == null ? step.getPlanQty() : planQty);
        d.setPlanStart(step.getPlanStart());
        d.setPlanEnd(step.getPlanEnd());
        d.setDispatchTime(LocalDateTime.now());
        d.setStatus("DISPATCHED");
        d.setNcProgramNo(step.getNcProgramNo());
        dispatchMapper.insert(d);

        step.setStatus("DISPATCHED");
        stepMapper.updateById(step);
        if (!"IN_PROGRESS".equals(order.getStatus()) && !"FINISHED".equals(order.getStatus())) {
            order.setStatus("IN_PROGRESS");
            orderMapper.updateById(order);
        }
        return d;
    }

    @Transactional
    public void receiveDispatch(Long dispatchId) {
        ProdDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) throw new BizException("派工单不存在");
        d.setStatus("RECEIVED");
        d.setReceiveTime(LocalDateTime.now());
        dispatchMapper.updateById(d);
    }

    // ==================================================================
    //  4. 车间报工
    // ==================================================================

    /**
     * 开工：派工单状态置 RUNNING，工序任务置 RUNNING，并记录机床状态跃迁。
     *
     * <p>开工前先做状态机校验 —— 已完工/已取消的派工单、已完工的工序任务都不允许再开工，
     * 否则（这是修复前真实发生过的）对一张 FINISHED 的派工单再点一次「开工」，
     * 工序任务会从 FINISHED 退回 RUNNING，后续报工继续累加数量，
     * 而订单仍是「已完成」，同一张工单出现两套互相矛盾的状态。
     */
    @Transactional
    public Map<String, Object> startWork(Long dispatchId, String ncProgramNo) {
        ProdDispatch d = requireDispatch(dispatchId);
        ProdOrderStep step = stepMapper.selectById(d.getStepId());
        if (step == null) throw new BizException("工序任务不存在");
        ProdOrder order = requireOrder(d.getOrderId());
        requireReportable(d, step, order);
        LocalDateTime now = LocalDateTime.now();

        d.setStatus("RUNNING");
        if (d.getReceiveTime() == null) d.setReceiveTime(now);
        dispatchMapper.updateById(d);

        step.setStatus("RUNNING");
        if (step.getActualStart() == null) step.setActualStart(now);
        if (ncProgramNo != null && !ncProgramNo.isBlank()) {
            if (step.getNcProgramNo() != null && !step.getNcProgramNo().equals(ncProgramNo)) {
                throw new BizException("程序号校验不通过：工艺要求 " + step.getNcProgramNo()
                        + "，机床实际执行 " + ncProgramNo + "。请确认后再开工。");
            }
            step.setNcProgramNo(ncProgramNo);
        }
        stepMapper.updateById(step);

        ProdTaskReport r = new ProdTaskReport();
        r.setReportNo(CodeGen.next("RP"));
        r.setDispatchId(d.getId());
        r.setOrderId(d.getOrderId());
        r.setOrderNo(d.getOrderNo());
        r.setStepId(step.getId());
        r.setMachineId(d.getMachineId());
        r.setMachineCode(d.getMachineCode());
        r.setEmployeeId(d.getEmployeeId());
        r.setEmployeeName(d.getEmployeeName());
        r.setShiftId(d.getShiftId());
        r.setShiftName(d.getShiftName());
        r.setReportType("开工");
        r.setWorkDate(now.toLocalDate());
        r.setStartTime(now);
        r.setRunMinutes(BigDecimal.ZERO);
        r.setStopMinutes(BigDecimal.ZERO);
        r.setQty(BigDecimal.ZERO);
        r.setQualifiedQty(BigDecimal.ZERO);
        r.setDefectQty(BigDecimal.ZERO);
        r.setNcProgramNo(step.getNcProgramNo());
        r.setReportTime(now);
        reportMapper.insert(r);

        writeStatusLog(d.getMachineId(), d.getMachineCode(), "RUN", "开工：" + d.getOrderNo() + " " + d.getProcName(),
                d.getOrderNo(), step.getNcProgramNo());
        return Map.of("dispatchNo", d.getDispatchNo(), "reportNo", r.getReportNo(), "startTime", now.toString());
    }

    /**
     * 报工 / 完工：写入报工记录，并逐级联动工序任务与订单数量。
     *
     * @param finish true 表示完工（工序任务置 FINISHED，若全部工序完工则订单置 FINISHED）
     */
    @Transactional
    public Map<String, Object> reportWork(ReportRequest req, boolean finish) {
        ProdDispatch d = requireDispatch(req.dispatchId());
        ProdOrderStep step = stepMapper.selectById(d.getStepId());
        ProdOrder order = requireOrder(d.getOrderId());
        requireReportable(d, step, order);
        LocalDateTime now = LocalDateTime.now();

        BigDecimal qty = nzBd(req.qty());
        BigDecimal defect = nzBd(req.defectQty());
        if (qty.doubleValue() <= 0) throw new BizException("报工数量必须大于 0");
        if (defect.doubleValue() > qty.doubleValue()) throw new BizException("不良数量不能大于报工数量");
        BigDecimal qualified = qty.subtract(defect);

        double runMinutes = req.runMinutes() == null
                ? Math.max(1, Duration.between(step.getActualStart() == null ? now.minusMinutes(30) : step.getActualStart(), now).toMinutes())
                : req.runMinutes();

        ProdTaskReport r = new ProdTaskReport();
        r.setReportNo(CodeGen.next("RP"));
        r.setDispatchId(d.getId());
        r.setOrderId(d.getOrderId());
        r.setOrderNo(d.getOrderNo());
        r.setStepId(step.getId());
        r.setMachineId(d.getMachineId());
        r.setMachineCode(d.getMachineCode());
        r.setEmployeeId(req.employeeId() == null ? d.getEmployeeId() : req.employeeId());
        r.setEmployeeName(req.employeeName() == null ? d.getEmployeeName() : req.employeeName());
        r.setShiftId(d.getShiftId());
        r.setShiftName(req.shiftName() == null ? d.getShiftName() : req.shiftName());
        r.setReportType(finish ? "完工" : "报工");
        r.setWorkDate(now.toLocalDate());
        r.setStartTime(req.startTime() == null ? (step.getActualStart() == null ? now.minusMinutes((long) runMinutes) : step.getActualStart()) : req.startTime());
        r.setEndTime(now);
        r.setRunMinutes(BigDecimal.valueOf(runMinutes).setScale(2, RoundingMode.HALF_UP));
        r.setStopMinutes(BigDecimal.valueOf(req.stopMinutes() == null ? 0 : req.stopMinutes()).setScale(2, RoundingMode.HALF_UP));
        r.setQty(qty);
        r.setQualifiedQty(qualified);
        r.setDefectQty(defect);
        r.setScrapReason(req.scrapReason());
        r.setNcProgramNo(req.ncProgramNo() == null ? step.getNcProgramNo() : req.ncProgramNo());
        r.setToolCode(req.toolCode());
        r.setToolOffsetNo(req.toolOffsetNo());
        r.setReportTime(now);
        r.setRemark(req.remark());
        reportMapper.insert(r);

        // ---- 工序任务联动 ----
        /*
         * 统计口径：以「已落库的报工记录」为准，不要再加上本次的 qty。
         *
         * 上面 reportMapper.insert(r) 已经把本次记录写进库了，而 sumByStep 统计的是 step_id 下的全部
         * 「报工/完工」记录 —— 所以再 + qty 就是把本次报工算了两遍（报 10 件 → 工序累计 20 件）。
         * 这里改成纯函数式的「以库为准」，避免再出现同类重复累加。
         */
        double totalFin = sumByStep(step.getId(), "qty");
        double totalDef = sumByStep(step.getId(), "defect_qty");
        step.setFinishedQty(bd(totalFin));
        step.setDefectQty(bd(totalDef));
        step.setQualifiedQty(bd(Math.max(0, totalFin - totalDef)));
        step.setRunMinutes(bd(nz(step.getRunMinutes()) + runMinutes));
        step.setStopMinutes(bd(nz(step.getStopMinutes()) + (req.stopMinutes() == null ? 0 : req.stopMinutes())));
        if (req.ncProgramNo() != null && !req.ncProgramNo().isBlank()) step.setNcProgramNo(req.ncProgramNo());
        boolean stepCompleted = finish || totalFin >= nz(step.getPlanQty());
        if (stepCompleted) {
            step.setStatus(step.getIsInspection() == 1 ? "INSPECTING" : "FINISHED");
            step.setActualEnd(now);
        } else {
            step.setStatus("RUNNING");
        }
        stepMapper.updateById(step);

        // ---- 订单联动 ----
        /*
         * 订单完工数量 = 最后一道工序（seqNo 最大）的完工数 —— 只有走完工艺路线最后一道工序才是成品。
         * 早先这里取的是各工序的最大值，等于用「最领先的那道工序产量」冒充成品数，
         * 多工序订单的完成率会系统性偏高（例：第 1 道做了 62 件、第 2 道才 30 件，却报 62 件成品）。
         * 工序进度另有 stepDone/stepCount 指标表达，不靠这个字段。
         */
        List<ProdOrderStep> all = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                .eq("order_id", order.getId()).orderByDesc("seq_no"));
        ProdOrderStep lastStep = all.isEmpty() ? null : all.get(0);
        double orderFin = lastStep == null ? 0 : nz(lastStep.getFinishedQty());
        double orderDef = lastStep == null ? 0 : nz(lastStep.getDefectQty());
        order.setFinishedQty(bd(orderFin));
        order.setDefectQty(bd(orderDef));
        order.setQualifiedQty(bd(Math.max(0, orderFin - orderDef)));
        boolean allDone = all.stream().allMatch(s -> "FINISHED".equals(s.getStatus()));
        if (allDone && orderFin >= nz(order.getPlanQty())) {
            order.setStatus("FINISHED");
        } else if (!"FINISHED".equals(order.getStatus())) {
            order.setStatus("IN_PROGRESS");
        }
        orderMapper.updateById(order);

        // ---- 派工单联动 ----
        /*
         * 工序任务一旦完成（报满计划量或显式完工），派工单必须同步关闭。
         * 早先只有显式「完工」才关闭派工单，于是「报满计划量」的工序会留下一个
         * RUNNING 的派工单，操作工再点一次报工就继续加数量 —— 这正是重复报工的入口。
         */
        if (stepCompleted) {
            d.setStatus("FINISHED");
            dispatchMapper.updateById(d);
            writeStatusLog(d.getMachineId(), d.getMachineCode(), "IDLE",
                    "完工：" + d.getOrderNo() + " " + d.getProcName(), d.getOrderNo(), step.getNcProgramNo());
        }

        // ---- 追溯记录联动 ----
        appendTraceLog(order, step, d, qty, runMinutes, all);

        return Map.of("reportNo", r.getReportNo(), "stepStatus", step.getStatus(),
                "orderStatus", order.getStatus(), "finishedQty", step.getFinishedQty());
    }

    /**
     * 写序列号级追溯记录。
     *
     * <p>口径：序列号 = 一件产品。一次报工 N 件，就为「本工序还没有记录过的」前 N 个序列号各写一条
     * 记录，每条只记 1 件。修复前的实现有三个问题：
     * <ol>
     *   <li>写死 {@code limit 5}：一张工单的第 6 个序列号起，追溯链直接断掉；</li>
     *   <li>每次报工都给同一批序列号再写一遍：正向追溯的时间线上全是重复工序行；</li>
     *   <li>每条记录都写整批 qty：报 10 件就给每个序列号记 10 件，数量无从解释。</li>
     * </ol>
     * 另外补齐毛坯批次（取序列号自己的批次号）—— 不写这一列，「按炉号反查受影响序列号」
     * 这条反向追溯链路在真实报工数据上永远是空的（只有演示数据生成器会写它）。
     *
     * @param allSteps 该工单的全部工序任务（用于判断是否走到了最后一道工序）
     */
    private void appendTraceLog(ProdOrder order, ProdOrderStep step, ProdDispatch d, BigDecimal qty,
                                double runMinutes, List<ProdOrderStep> allSteps) {
        int pieces = (int) Math.floor(nz(qty));
        if (pieces <= 0) return;

        List<TraceSn> sns = snMapper.selectList(new QueryWrapper<TraceSn>()
                .eq("order_id", order.getId()).ne("status", "报废").orderByAsc("id"));
        if (sns.isEmpty()) return;

        int seqNo = step.getSeqNo() == null ? 0 : step.getSeqNo();
        // 本工序已经记录过的序列号：避免同一工序被反复写入（这是「时间线重复行」的根因）
        Set<String> covered = traceLogMapper.selectList(new QueryWrapper<TraceLog>()
                        .eq("step_id", step.getId()).isNotNull("sn_code").select("sn_code"))
                .stream().map(TraceLog::getSnCode).collect(Collectors.toSet());

        List<TraceSn> targets = sns.stream()
                .filter(s -> !covered.contains(s.getSnCode()))
                // 只有已经流到本工序（或更前面工序）的序列号，才可能在本工序被加工
                .filter(s -> s.getCurrentStep() == null || s.getCurrentStep() <= seqNo)
                .limit(pieces)
                .toList();
        if (targets.isEmpty()) return;

        ToolLedger tool = d.getMachineId() == null ? null
                : toolMapper.selectOne(new QueryWrapper<ToolLedger>().eq("machine_id", d.getMachineId()).last("limit 1"));
        boolean lastStep = allSteps.stream().allMatch(s -> s.getSeqNo() == null || s.getSeqNo() <= seqNo);
        LocalDateTime now = LocalDateTime.now();
        double minutesPerPiece = runMinutes / targets.size();

        for (TraceSn sn : targets) {
            TraceLog tl = new TraceLog();
            tl.setSnCode(sn.getSnCode());
            tl.setOrderId(order.getId());
            tl.setOrderNo(order.getOrderNo());
            tl.setStepId(step.getId());
            tl.setSeqNo(step.getSeqNo());
            tl.setProcName(step.getProcName());
            tl.setMachineId(d.getMachineId());
            tl.setMachineCode(d.getMachineCode());
            tl.setEmployeeId(d.getEmployeeId());
            tl.setOperator(d.getEmployeeName());
            tl.setNcProgramNo(step.getNcProgramNo());
            tl.setToolCode(tool == null ? null : tool.getToolCode());
            tl.setToolOffsetNo(tool == null ? null : tool.getOffsetNo());
            // 毛坯批次取自「这个序列号自己」的投料批次，按批次反查才是准的
            tl.setMaterialBatch(sn.getBatchNo());
            tl.setStartTime(now.minusMinutes((long) runMinutes));
            tl.setEndTime(now);
            tl.setQty(BigDecimal.ONE);
            tl.setRunMinutes(bd(minutesPerPiece));
            tl.setQcResult("待检");
            tl.setShiftName(d.getShiftName());
            tl.setParamJson("{\"来源\":\"车间报工\",\"程序版本\":\"" + step.getNcProgramVer() + "\"}");
            traceLogMapper.insert(tl);

            sn.setCurrentStep(seqNo);
            sn.setCurrentProc(step.getProcName());
            sn.setMachineCode(d.getMachineCode());
            if (lastStep && !"完工".equals(sn.getStatus())) {
                sn.setStatus("完工");
                sn.setCompleteTime(now);
            }
            snMapper.updateById(sn);
        }
    }

    /**
     * 记录机床状态跃迁：关掉上一条未结束的记录、写新记录，并把 md_machine 的当前状态同步过去。
     *
     * <p>公开给车间终端复用（{@code /terminal/machineState}）：原来终端是自己写状态日志的，
     * 忘了同步 {@code md_machine.run_state}，于是「主数据里显示运行中、日志里显示保养中」。
     */
    public void writeStatusLog(Long machineId, String machineCode, String status, String reason, String orderNo, String program) {
        if (machineId == null) return;
        ProdMachineStatusLog last = statusLogMapper.selectOne(new QueryWrapper<ProdMachineStatusLog>()
                .eq("machine_id", machineId).isNull("end_time").orderByDesc("start_time").last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (last != null) {
            last.setEndTime(now);
            last.setDurationSec(Duration.between(last.getStartTime(), now).getSeconds());
            statusLogMapper.updateById(last);
        }
        ProdMachineStatusLog log = new ProdMachineStatusLog();
        log.setMachineId(machineId);
        log.setMachineCode(machineCode);
        log.setStatus(status);
        log.setPrevStatus(last == null ? null : last.getStatus());
        log.setReason(reason);
        log.setOrderNo(orderNo);
        log.setNcProgramNo(program);
        log.setStartTime(now);
        log.setOperator("系统");
        statusLogMapper.insert(log);

        MdMachine m = machineMapper.selectById(machineId);
        if (m != null) {
            m.setRunState(status);
            m.setStatus(switch (status) {
                case "RUN" -> "RUN";
                case "IDLE" -> "IDLE";
                case "STOP" -> "STOP";
                case "ALARM" -> "ALARM";
                case "MAINT" -> "MAINT";
                case "OFFLINE" -> "OFFLINE";
                default -> m.getStatus();
            });
            machineMapper.updateById(m);
        }
    }

    // ==================================================================
    //  5. 齐套检查
    // ==================================================================

    /** 按 BOM 折算毛坯/标准件需求量，与当前库存对比。CNC 车间停工的头号原因就是毛坯不齐套。 */
    public Map<String, Object> kitCheck(Long orderId) {
        ProdOrder order = requireOrder(orderId);
        List<Map<String, Object>> items = new ArrayList<>();
        boolean ready = true;

        MdBom bom = bomMapper.selectOne(new QueryWrapper<MdBom>()
                .eq("product_mat_id", order.getProductMatId()).last("limit 1"));
        if (bom == null) {
            // 用 LinkedHashMap 而不是 Map.of：Map.of 不允许 null 值，
            // 一旦工单的产品/单号有历史空值就会直接抛 NPE（500），把「没维护 BOM」变成接口报错
            Map<String, Object> noBom = new LinkedHashMap<>();
            noBom.put("orderNo", order.getOrderNo());
            noBom.put("productName", order.getProductName());
            noBom.put("hasBom", false);
            noBom.put("ready", true);
            noBom.put("items", items);
            noBom.put("message", "该产品未维护 BOM，仅作毛坯库存核对");
            return noBom;
        }
        List<MdBomItem> bomItems = bomItemMapper.selectList(new QueryWrapper<MdBomItem>().eq("bom_id", bom.getId()));
        double qty = nz(order.getPlanQty());
        for (MdBomItem bi : bomItems) {
            MdMaterial mat = bi.getMatId() == null ? null : materialMapper.selectById(bi.getMatId());
            double need = nz(bi.getQtyPer()) * qty * (1 + nz(bi.getScrapRate()) / 100.0);
            double stock = mat == null ? 0 : nz(mat.getCurrentStock());
            boolean enough = stock >= need;
            if (!enough) ready = false;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("matCode", bi.getMatCode());
            m.put("matName", bi.getMatName());
            m.put("matType", bi.getItemType());
            m.put("qtyPer", bi.getQtyPer());
            m.put("scrapRate", bi.getScrapRate());
            m.put("needQty", Math.round(need * 100) / 100.0);
            m.put("stockQty", stock);
            m.put("unit", mat == null ? null : mat.getUnit());
            m.put("gapQty", enough ? 0 : Math.round((need - stock) * 100) / 100.0);
            m.put("enough", enough);
            items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("productName", order.getProductName());
        result.put("planQty", order.getPlanQty());
        result.put("hasBom", true);
        result.put("ready", ready);
        result.put("items", items);
        result.put("message", ready ? "物料齐套，可安排生产" : "物料不齐套，请先补料或调整计划");
        return result;
    }

    // ==================================================================
    //  工具
    // ==================================================================

    /**
     * 报工/开工前的前置校验：状态机不允许的操作一律拒绝。
     *
     * <p>这是「已完工的工序还能继续报工」这类数据事故的唯一防线 —— 校验放在 Service 层，
     * 无论请求来自车间终端、计划员页面还是直接调接口都绕不过去。
     */
    private void requireReportable(ProdDispatch d, ProdOrderStep step, ProdOrder order) {
        if (d == null || step == null) throw new BizException(BizException.NOT_FOUND, "派工单或工序任务不存在");
        if ("FINISHED".equals(d.getStatus()) || "CANCELED".equals(d.getStatus())) {
            throw new BizException(BizException.CONFLICT, "派工单 " + d.getDispatchNo() + " 已是「"
                    + d.getStatus() + "」状态，不能重复开工或报工");
        }
        if ("FINISHED".equals(step.getStatus())) {
            throw new BizException(BizException.CONFLICT, "工序「" + step.getProcName()
                    + "」已完工（累计 " + nz(step.getFinishedQty()) + " 件），如需补报请先由计划员重新下达该工序");
        }
        if ("CANCELED".equals(step.getStatus())) {
            throw new BizException(BizException.CONFLICT, "工序「" + step.getProcName() + "」已取消");
        }
        if ("FINISHED".equals(order.getStatus()) || "CANCELED".equals(order.getStatus())) {
            throw new BizException(BizException.CONFLICT, "工单 " + order.getOrderNo() + " 已是「"
                    + order.getStatus() + "」状态，不能继续报工");
        }
    }

    private ProdOrder requireOrder(Long id) {
        ProdOrder o = orderMapper.selectById(id);
        if (o == null) throw new BizException(BizException.NOT_FOUND, "生产订单不存在：" + id);
        return o;
    }

    private ProdDispatch requireDispatch(Long id) {
        ProdDispatch d = dispatchMapper.selectById(id);
        if (d == null) throw new BizException(BizException.NOT_FOUND, "派工单不存在：" + id);
        return d;
    }

    private double sumByStep(Long stepId, String column) {
        return reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                        .eq("step_id", stepId)
                        .ne("report_type", "开工"))
                .stream().mapToDouble(x -> "qty".equals(column) ? nz(x.getQty()) : nz(x.getDefectQty())).sum();
    }

    private static double nz(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static double nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static BigDecimal nzBd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** 报工请求。 */
    public record ReportRequest(Long dispatchId, BigDecimal qty, BigDecimal defectQty, Double runMinutes,
                                Double stopMinutes, String scrapReason, String ncProgramNo, String toolCode,
                                String toolOffsetNo, LocalDateTime startTime, Long employeeId, String employeeName,
                                String shiftName, String remark) {
    }

    /** 补全停机记录的派生字段（时长、默认状态）。 */
    public ProdStopRecord fillStop(ProdStopRecord s) {
        if (s.getStartTime() != null && s.getEndTime() != null) {
            s.setDurationMin(bd(Duration.between(s.getStartTime(), s.getEndTime()).toMinutes()));
        }
        if (s.getStatus() == null) s.setStatus("已关闭");
        return s;
    }

    /** 供其它服务复用的停机写库（保留统一入口，避免各处重复造轮子）。 */
    public ProdStopRecord saveStop(ProdStopRecord s) {
        fillStop(s);
        stopMapper.insert(s);
        return s;
    }

    /** 供看板复用的「今日已完工数量」。 */
    public double todayOutput(String machineCode) {
        List<ProdTaskReport> list = reportMapper.selectList(new QueryWrapper<ProdTaskReport>()
                .eq("work_date", LocalDate.now())
                .eq(machineCode != null, "machine_code", machineCode));
        return list.stream().mapToDouble(r -> nz(r.getQty())).sum();
    }
}
