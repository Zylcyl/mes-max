package com.mesmax.boot;

import com.mesmax.common.CodeGen;
import com.mesmax.entity.*;
import com.mesmax.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 演示业务流水生成器。
 *
 * <p>为什么要用代码生成而不是写死在 SQL 里？
 * 因为「订单 → 工序任务 → 派工 → 报工 → 停机 → 检验 → 不合格 → 追溯」之间存在强一致性约束：
 * 报工数量必须等于工序任务完工数量、订单完工数量又必须等于各工序之和、
 * 追溯记录必须引用真实存在的机床/程序/刀具/批次。手写 SQL 几乎必然出现对不上的数据。
 *
 * <p>生成器使用固定随机种子（{@code 20260921}），因此每次初始化得到完全相同的数据集，
 * 便于答辩演示与回归测试。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final long SEED = 20260921L;

    private final Random rnd = new Random(SEED);

    private final MdMachineMapper machineMapper;
    private final MdMaterialMapper materialMapper;
    private final MdRoutingMapper routingMapper;
    private final MdRoutingStepMapper routingStepMapper;
    private final MdCustomerMapper customerMapper;
    private final MdEmployeeMapper employeeMapper;
    private final MdShiftMapper shiftMapper;
    private final MdWorkCenterMapper workCenterMapper;
    private final ToolLedgerMapper toolMapper;
    private final EqCheckItemMapper checkItemMapper;
    private final EqMaintPlanMapper maintPlanMapper;
    private final TraceMaterialBatchMapper batchMapper;

    private final ProdOrderMapper orderMapper;
    private final ProdOrderStepMapper stepMapper;
    private final ProdDispatchMapper dispatchMapper;
    private final ProdTaskReportMapper reportMapper;
    private final ProdStopRecordMapper stopMapper;
    private final ProdMachineStatusLogMapper statusLogMapper;
    private final ProdMachineTelemetryMapper telemetryMapper;
    private final ProdAndonMapper andonMapper;
    private final QcInspectionMapper inspMapper;
    private final QcInspectionItemMapper inspItemMapper;
    private final QcDefectMapper defectMapper;
    private final QcSpcSampleMapper spcMapper;
    private final TraceSnMapper snMapper;
    private final TraceLogMapper traceLogMapper;
    private final ToolUsageMapper toolUsageMapper;
    private final ToolGrindMapper toolGrindMapper;
    private final ToolMeasureMapper toolMeasureMapper;
    private final EqCheckRecordMapper checkRecordMapper;
    private final EqFaultMapper faultMapper;
    private final EqMaintOrderMapper maintOrderMapper;

    @Value("${mes-max.demo-data.enabled:true}")
    private boolean enabled;
    @Value("${mes-max.demo-data.history-days:14}")
    private int historyDays;

    public DemoDataInitializer(MdMachineMapper machineMapper, MdMaterialMapper materialMapper,
                               MdRoutingMapper routingMapper, MdRoutingStepMapper routingStepMapper,
                               MdCustomerMapper customerMapper, MdEmployeeMapper employeeMapper,
                               MdShiftMapper shiftMapper, MdWorkCenterMapper workCenterMapper,
                               ToolLedgerMapper toolMapper, EqCheckItemMapper checkItemMapper,
                               EqMaintPlanMapper maintPlanMapper, TraceMaterialBatchMapper batchMapper,
                               ProdOrderMapper orderMapper, ProdOrderStepMapper stepMapper,
                               ProdDispatchMapper dispatchMapper, ProdTaskReportMapper reportMapper,
                               ProdStopRecordMapper stopMapper, ProdMachineStatusLogMapper statusLogMapper,
                               ProdMachineTelemetryMapper telemetryMapper, ProdAndonMapper andonMapper,
                               QcInspectionMapper inspMapper, QcInspectionItemMapper inspItemMapper,
                               QcDefectMapper defectMapper, QcSpcSampleMapper spcMapper,
                               TraceSnMapper snMapper, TraceLogMapper traceLogMapper,
                               ToolUsageMapper toolUsageMapper, ToolGrindMapper toolGrindMapper,
                               ToolMeasureMapper toolMeasureMapper, EqCheckRecordMapper checkRecordMapper,
                               EqFaultMapper faultMapper, EqMaintOrderMapper maintOrderMapper) {
        this.machineMapper = machineMapper;
        this.materialMapper = materialMapper;
        this.routingMapper = routingMapper;
        this.routingStepMapper = routingStepMapper;
        this.customerMapper = customerMapper;
        this.employeeMapper = employeeMapper;
        this.shiftMapper = shiftMapper;
        this.workCenterMapper = workCenterMapper;
        this.toolMapper = toolMapper;
        this.checkItemMapper = checkItemMapper;
        this.maintPlanMapper = maintPlanMapper;
        this.batchMapper = batchMapper;
        this.orderMapper = orderMapper;
        this.stepMapper = stepMapper;
        this.dispatchMapper = dispatchMapper;
        this.reportMapper = reportMapper;
        this.stopMapper = stopMapper;
        this.statusLogMapper = statusLogMapper;
        this.telemetryMapper = telemetryMapper;
        this.andonMapper = andonMapper;
        this.inspMapper = inspMapper;
        this.inspItemMapper = inspItemMapper;
        this.defectMapper = defectMapper;
        this.spcMapper = spcMapper;
        this.snMapper = snMapper;
        this.traceLogMapper = traceLogMapper;
        this.toolUsageMapper = toolUsageMapper;
        this.toolGrindMapper = toolGrindMapper;
        this.toolMeasureMapper = toolMeasureMapper;
        this.checkRecordMapper = checkRecordMapper;
        this.faultMapper = faultMapper;
        this.maintOrderMapper = maintOrderMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("演示数据生成已关闭（mes-max.demo-data.enabled=false）");
            return;
        }
        Long exist = orderMapper.selectCount(null);
        if (exist != null && exist > 0) {
            log.info("已存在 {} 条生产订单，跳过演示流水生成", exist);
            return;
        }
        long t0 = System.currentTimeMillis();
        Ctx c = new Ctx();
        log.info("开始生成 CNC 车间演示流水（历史 {} 天）…", historyDays);
        genOrders(c);
        genDispatches(c);
        genReports(c);
        genStops(c);
        genStatusLogs(c);
        genTelemetryHistory(c);
        genInspections(c);
        genDefects(c);
        genSpc(c);
        genTrace(c);
        genToolRecords(c);
        genEquipmentRecords(c);
        genAndon(c);
        log.info("演示流水生成完成：订单 {}，工序任务 {}，报工 {}，耗时 {} ms",
                c.orders.size(), c.allSteps.size(), c.reports.size(), System.currentTimeMillis() - t0);
    }

    // ==================================================================
    //  上下文
    // ==================================================================

    private class Ctx {
        final List<MdMachine> machines = machineMapper.selectList(null);
        final List<MdMaterial> materials = materialMapper.selectList(null);
        final List<MdRouting> routings = routingMapper.selectList(null);
        final List<MdCustomer> customers = customerMapper.selectList(null);
        final List<MdEmployee> operators = employeeMapper.selectList(null).stream()
                .filter(e -> e.getPost() != null && e.getPost().contains("工")).toList();
        final List<MdShift> shifts = shiftMapper.selectList(null);
        final List<MdWorkCenter> workCenters = workCenterMapper.selectList(null);
        final List<ToolLedger> tools = toolMapper.selectList(null);
        final List<EqCheckItem> checkItems = checkItemMapper.selectList(null);
        final List<EqMaintPlan> maintPlans = maintPlanMapper.selectList(null);
        final List<TraceMaterialBatch> batches = batchMapper.selectList(null);

        final Map<Long, MdMaterial> materialById = new HashMap<>();
        final Map<Long, MdWorkCenter> wcById = new HashMap<>();
        final Map<Long, List<MdRoutingStep>> stepsByRouting = new HashMap<>();
        final Map<Long, List<MdMachine>> machinesByWc = new HashMap<>();
        final Map<Long, Integer> wcRoundRobin = new HashMap<>();
        final Map<Long, MdMachine> machineById = new HashMap<>();

        final List<ProdOrder> orders = new ArrayList<>();
        final List<ProdOrderStep> allSteps = new ArrayList<>();
        final List<ProdTaskReport> reports = new ArrayList<>();
        final List<ProdOrderStep> runningSteps = new ArrayList<>();
        final Map<Long, ProdDispatch> dispatchByStep = new HashMap<>();

        Ctx() {
            materials.forEach(m -> materialById.put(m.getId(), m));
            workCenters.forEach(w -> wcById.put(w.getId(), w));
            machines.forEach(m -> machineById.put(m.getId(), m));
            for (MdRoutingStep s : routingStepMapper.selectList(null)) {
                stepsByRouting.computeIfAbsent(s.getRoutingId(), k -> new ArrayList<>()).add(s);
            }
            stepsByRouting.values().forEach(l -> l.sort(Comparator.comparing(MdRoutingStep::getSeqNo)));
            for (MdMachine m : machines) {
                machinesByWc.computeIfAbsent(m.getWorkCenterId(), k -> new ArrayList<>()).add(m);
            }
        }

        /** 按工作中心轮转选机床（模拟真实车间的机台分配习惯）。 */
        MdMachine pickMachine(Long wcId) {
            List<MdMachine> list = machinesByWc.get(wcId);
            if (list == null || list.isEmpty()) return null;
            int idx = wcRoundRobin.merge(wcId, 1, Integer::sum) - 1;
            return list.get(idx % list.size());
        }
    }

    // ==================================================================
    //  1. 生产订单 + 工序任务
    // ==================================================================

    private void genOrders(Ctx c) {
        LocalDate today = LocalDate.now();
        int orderCount = 36;
        for (int i = 0; i < orderCount; i++) {
            MdRouting routing = c.routings.get(i % c.routings.size());
            MdMaterial product = c.materialById.get(routing.getProductMatId());
            MdCustomer cust = c.customers.get(rnd.nextInt(c.customers.size()));

            boolean trial = i % 9 == 3;
            int qty = trial ? ri(3, 12) : ri(20, 200);

            /*
             * 工单计划窗口的分布刻意做成两类，保证演示时车间同时存在：
             *   · 在制工单（每 5 张取 2 张）：开工日落在「今天之前 0~2 天」，
             *     加上工序之间的真实间隔后，计划窗口正好横跨当前时刻 → 会有一道工序处于加工中；
             *   · 其余工单：分散在过去 13 天 ~ 未来 6 天，形成已完工 / 待排产的完整时间轴。
             * 如果全部随机分散，演示时车间会出现「所有机台都没在制工单」的假象。
             */
            boolean liveWindow = i % 5 < 2;
            LocalDate startDay = liveWindow
                    ? today.minusDays(ri(0, 2))
                    : today.minusDays(13).plusDays(rnd.nextInt(20));
            LocalDateTime cursor = startDay.atTime(ri(7, 9), rnd.nextBoolean() ? 0 : 30);

            ProdOrder o = new ProdOrder();
            o.setOrderNo(CodeGen.next("WO"));
            o.setCustomerId(cust.getId());
            o.setCustomerName(cust.getCustName());
            o.setProductMatId(product == null ? null : product.getId());
            o.setProductCode(product == null ? routing.getRoutingCode() : product.getMatCode());
            o.setProductName(product == null ? routing.getRoutingName() : product.getMatName());
            o.setProductSpec(product == null ? null : product.getSpec());
            o.setPlanQty(bd(qty));
            o.setRoutingId(routing.getId());
            o.setOrderType(trial ? "试制" : (i % 13 == 5 ? "返修" : "量产"));
            o.setSource(i % 4 == 0 ? "ERP导入" : "手工录入");
            o.setSalesNo("SO" + (20260000 + ri(1000, 9999)));
            o.setPlanStart(cursor);
            o.setPriority(i % 11 == 0 ? "加急" : (i % 3 == 0 ? "高" : (i % 3 == 1 ? "中" : "低")));
            o.setPriorityScore(score(o.getPriority(), cust.getCustLevel(), o.getOrderType(), startDay, today));
            o.setIsLocked(0);
            o.setNcProgramNo(null);
            o.setFinishedQty(BigDecimal.ZERO);
            o.setQualifiedQty(BigDecimal.ZERO);
            o.setDefectQty(BigDecimal.ZERO);
            o.setRemark(trial ? "客户样件，需首件全检" : null);
            orderMapper.insert(o);
            c.orders.add(o);

            List<MdRoutingStep> route = c.stepsByRouting.getOrDefault(routing.getId(), List.of());
            for (MdRoutingStep rs : route) {
                ProdOrderStep st = new ProdOrderStep();
                st.setOrderId(o.getId());
                st.setOrderNo(o.getOrderNo());
                st.setSeqNo(rs.getSeqNo());
                st.setProcId(rs.getProcId());
                st.setProcCode(rs.getProcCode());
                st.setProcName(rs.getProcName());
                st.setWcId(rs.getWcId());
                st.setWcName(rs.getWcName());
                MdMachine mac = c.pickMachine(rs.getWcId());
                if (mac != null) {
                    st.setMachineId(mac.getId());
                    st.setMachineCode(mac.getMacCode());
                }
                st.setPlanQty(bd(qty));
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
                double workMin = rs.getSetupTime().doubleValue() + rs.getPieceTime().doubleValue() * qty;
                st.setPlanEnd(cursor.plusMinutes(Math.max(45, Math.round(workMin))));
                // 工序之间留出换刀、换型、等待检验与排队的真实间隔
                cursor = st.getPlanEnd().plusHours(ri(2, 10));
                stepMapper.insert(st);
                c.allSteps.add(st);
            }
            o.setPlanEnd(cursor);
            orderMapper.updateById(o);
        }

        // 依据时间轴回填订单/工序状态
        LocalDateTime now = LocalDateTime.now();
        for (ProdOrder o : c.orders) {
            String status;
            if (o.getPlanEnd().isBefore(now.minusHours(6))) {
                status = rnd.nextDouble() < 0.9 ? "FINISHED" : "IN_PROGRESS";
            } else if (!o.getPlanStart().isAfter(now)) {
                status = "IN_PROGRESS";
            } else {
                status = rnd.nextDouble() < 0.4 ? "RELEASED" : "SCHEDULED";
            }
            if (o.getOrderType().equals("返修") && status.equals("IN_PROGRESS")) status = "HOLD";
            o.setStatus(status);
            orderMapper.updateById(o);

            List<ProdOrderStep> steps = c.allSteps.stream()
                    .filter(s -> s.getOrderId().equals(o.getId()))
                    .sorted(Comparator.comparing(ProdOrderStep::getSeqNo))
                    .toList();

            /*
             * 在制工单的工序状态按「唯一当前工序」推导，而不是单纯按时间判断：
             * 排在前面的工序已完工 → 第一道未完工的工序就是当前在制工序（RUNNING / INSPECTING）
             * → 其余工序按计划时间落在「已派工 / 待派工」。
             *
             * 这样才能保证：每张在制工单恰好有一道工序正在加工（现场就是这么走的），
             * 而不是出现「所有工序都还没到点、全都在待派工」这种不符合现场的分布。
             */
            boolean currentReached = false;
            for (ProdOrderStep st : steps) {
                String ss;
                if ("FINISHED".equals(status)) {
                    ss = "FINISHED";
                } else if ("IN_PROGRESS".equals(status)) {
                    if (!currentReached && st.getPlanEnd().isBefore(now)) {
                        ss = "FINISHED";
                    } else if (!currentReached) {
                        currentReached = true;
                        ss = st.getMachineId() == null ? "PENDING"
                                : (st.getIsInspection() == 1 ? "INSPECTING" : "RUNNING");
                    } else {
                        ss = st.getPlanStart().isBefore(now.plusHours(12)) ? "DISPATCHED" : "PENDING";
                    }
                } else {
                    ss = "PENDING";
                }
                st.setStatus(ss);
                if ("FINISHED".equals(ss)) {
                    st.setFinishedQty(st.getPlanQty());
                    st.setActualStart(st.getPlanStart());
                    st.setActualEnd(st.getPlanEnd());
                    st.setRunMinutes(bd(st.getSetupTime().doubleValue() + st.getPieceTime().doubleValue() * st.getPlanQty().doubleValue()));
                } else if ("RUNNING".equals(ss) || "INSPECTING".equals(ss)) {
                    double ratio = 0.25 + rnd.nextDouble() * 0.55;
                    st.setFinishedQty(bd(Math.max(1, Math.round(st.getPlanQty().doubleValue() * ratio))));
                    st.setActualStart(st.getPlanStart());
                    st.setRunMinutes(bd(st.getSetupTime().doubleValue() + st.getPieceTime().doubleValue() * st.getFinishedQty().doubleValue()));
                    c.runningSteps.add(st);
                } else {
                    st.setRunMinutes(BigDecimal.ZERO);
                }
                stepMapper.updateById(st);
            }
        }
    }

    /** JobShop 加权优先级评分 —— 交期紧迫度 + 客户等级 + 订单类型 三维加权（参考 IMES 项目做法）。 */
    private int score(String priority, String custLevel, String orderType, LocalDate planStart, LocalDate today) {
        int s = 40;
        if ("加急".equals(priority)) s += 35;
        else if ("高".equals(priority)) s += 22;
        else if ("中".equals(priority)) s += 10;
        if ("A".equals(custLevel)) s += 15;
        else if ("B".equals(custLevel)) s += 7;
        if ("试制".equals(orderType)) s += 10;
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, planStart);
        if (daysLeft < 0) s += 12;
        else if (daysLeft <= 3) s += 8;
        return Math.min(99, s);
    }

    // ==================================================================
    //  2. 派工
    // ==================================================================

    private void genDispatches(Ctx c) {
        int idx = 0;
        for (ProdOrderStep st : c.allSteps) {
            if ("PENDING".equals(st.getStatus())) continue;
            if (st.getMachineId() == null && !"FINISHED".equals(st.getStatus())) continue;

            ProdOrder o = c.orders.stream().filter(x -> x.getId().equals(st.getOrderId())).findFirst().orElse(null);
            if (o == null) continue;
            MdEmployee op = c.operators.get(idx++ % c.operators.size());
            MdShift shift = c.shifts.get(idx % c.shifts.size());

            ProdDispatch d = new ProdDispatch();
            d.setDispatchNo(CodeGen.next("DP"));
            d.setOrderId(o.getId());
            d.setOrderNo(o.getOrderNo());
            d.setStepId(st.getId());
            d.setSeqNo(st.getSeqNo());
            d.setProcName(st.getProcName());
            d.setMachineId(st.getMachineId());
            d.setMachineCode(st.getMachineCode());
            d.setEmployeeId(op.getId());
            d.setEmployeeName(op.getEmpName());
            d.setTeamName(op.getTeamName());
            d.setShiftId(shift.getId());
            d.setShiftName(shift.getShiftName());
            d.setPlanQty(st.getPlanQty());
            d.setPlanStart(st.getPlanStart());
            d.setPlanEnd(st.getPlanEnd());
            d.setDispatchTime(st.getPlanStart().minusHours(ri(2, 12)));
            d.setNcProgramNo(st.getNcProgramNo());
            d.setStatus(switch (st.getStatus()) {
                case "RUNNING" -> "RUNNING";
                case "FINISHED" -> "FINISHED";
                case "INSPECTING" -> "RUNNING";
                default -> rnd.nextDouble() < 0.5 ? "DISPATCHED" : "RECEIVED";
            });
            if (!"DISPATCHED".equals(d.getStatus())) d.setReceiveTime(d.getDispatchTime().plusMinutes(ri(5, 40)));
            dispatchMapper.insert(d);
            c.dispatchByStep.put(st.getId(), d);
        }
    }

    // ==================================================================
    //  3. 报工（OEE 与产量数据的来源）
    // ==================================================================

    private void genReports(Ctx c) {
        for (ProdOrderStep st : c.allSteps) {
            ProdDispatch d = c.dispatchByStep.get(st.getId());
            if (d == null) continue;
            if (!"FINISHED".equals(st.getStatus()) && !"RUNNING".equals(st.getStatus()) && !"INSPECTING".equals(st.getStatus())) continue;

            double totalRun = st.getRunMinutes() == null ? 0 : st.getRunMinutes().doubleValue();
            if (totalRun <= 0) continue;
            int slices = Math.min(3, Math.max(1, (int) Math.round(totalRun / 300)));
            double remainRun = totalRun;
            double remainQty = st.getFinishedQty().doubleValue();
            LocalDateTime cursor = st.getActualStart() == null ? st.getPlanStart() : st.getActualStart();

            /*
             * 先写一条「开工」占位记录，数量与工时都为 0 —— 与生产代码 ProductionService.startWork
             * 的写入口径保持一致。早先这条开工记录直接带着整批数量与工时，导致后续真实报工调用
             * sumByStep 汇总时把同一批数量再加一遍（数量翻倍）。
             */
            ProdTaskReport startRow = emptyReport(d, st, cursor);
            reportMapper.insert(startRow);
            c.reports.add(startRow);

            for (int k = 0; k < slices; k++) {
                boolean last = k == slices - 1;
                double run = last ? remainRun : Math.max(60, Math.round(remainRun / (slices - k) * (0.8 + rnd.nextDouble() * 0.4)));
                double qty = last ? remainQty : Math.max(1, Math.round(remainQty / (slices - k) * (0.8 + rnd.nextDouble() * 0.4)));
                remainRun -= run;
                remainQty -= qty;

                int defect = rnd.nextDouble() < 0.16 ? ri(1, Math.max(1, (int) Math.round(qty * 0.05))) : 0;
                int qualified = Math.max(0, (int) Math.round(qty) - defect);

                ProdTaskReport r = new ProdTaskReport();
                r.setReportNo(CodeGen.next("RP"));
                r.setDispatchId(d.getId());
                r.setOrderId(st.getOrderId());
                r.setOrderNo(st.getOrderNo());
                r.setStepId(st.getId());
                r.setMachineId(st.getMachineId());
                r.setMachineCode(st.getMachineCode());
                r.setEmployeeId(d.getEmployeeId());
                r.setEmployeeName(d.getEmployeeName());
                r.setShiftId(d.getShiftId());
                r.setShiftName(d.getShiftName());
                r.setReportType(last ? "完工" : "报工");
                r.setStartTime(cursor);
                LocalDateTime end = cursor.plusMinutes(Math.round(run * (1 + rnd.nextDouble() * 0.25)));
                r.setEndTime(end);
                r.setWorkDate(cursor.toLocalDate());
                r.setRunMinutes(bd(run));
                r.setStopMinutes(bd(Math.round(rnd.nextDouble() * 25)));
                r.setQty(bd(Math.round(qty)));
                r.setQualifiedQty(bd(qualified));
                r.setDefectQty(bd(defect));
                r.setScrapReason(defect > 0 ? pick(List.of("尺寸超差", "表面粗糙度", "崩刃刀痕", "碰伤划伤", "毛刺未清")) : null);
                r.setNcProgramNo(st.getNcProgramNo());
                ToolLedger tool = c.tools.stream().filter(t -> Objects.equals(t.getMachineId(), st.getMachineId())).findFirst().orElse(null);
                r.setToolCode(tool == null ? null : tool.getToolCode());
                r.setToolOffsetNo(tool == null ? null : tool.getOffsetNo());
                r.setReportTime(end);
                reportMapper.insert(r);
                c.reports.add(r);
                cursor = end.plusMinutes(ri(5, 60));
            }

            // 汇总只统计「报工/完工」记录（开工记录数量为 0，这里显式过滤以防将来写入口径变化）
            long q = Math.round(c.reports.stream()
                    .filter(x -> x.getStepId().equals(st.getId()) && !"开工".equals(x.getReportType()))
                    .mapToDouble(x -> x.getQty().doubleValue()).sum());
            long dq = Math.round(c.reports.stream()
                    .filter(x -> x.getStepId().equals(st.getId()) && !"开工".equals(x.getReportType()))
                    .mapToDouble(x -> x.getDefectQty().doubleValue()).sum());
            st.setFinishedQty(bd(q));
            st.setDefectQty(bd(dq));
            st.setQualifiedQty(bd(q - dq));
            if ("FINISHED".equals(st.getStatus())) st.setActualEnd(cursor);
            stepMapper.updateById(st);
        }

        // 回填订单完工数量/合格/不良与订单最终状态。
        // 订单完工数量取「最后一道工序」的完工数（成品数），不用各工序最大值，否则完成率虚高。
        for (ProdOrder o : c.orders) {
            List<ProdOrderStep> steps = c.allSteps.stream()
                    .filter(s -> s.getOrderId().equals(o.getId()))
                    .sorted(Comparator.comparing(ProdOrderStep::getSeqNo).reversed())
                    .toList();
            double fin = steps.isEmpty() ? 0 : steps.get(0).getFinishedQty().doubleValue();
            double def = steps.isEmpty() ? 0 : steps.get(0).getDefectQty().doubleValue();
            o.setFinishedQty(bd(fin));
            o.setDefectQty(bd(def));
            o.setQualifiedQty(bd(Math.max(0, fin - def)));
            if ("FINISHED".equals(o.getStatus()) && fin >= o.getPlanQty().doubleValue()) o.setStatus("FINISHED");
            orderMapper.updateById(o);
        }
    }

    /** 生成一条「开工」占位记录：数量、合格、不良与工时全部为 0。 */
    private ProdTaskReport emptyReport(ProdDispatch d, ProdOrderStep st, LocalDateTime start) {
        ProdTaskReport r = new ProdTaskReport();
        r.setReportNo(CodeGen.next("RP"));
        r.setDispatchId(d.getId());
        r.setOrderId(st.getOrderId());
        r.setOrderNo(st.getOrderNo());
        r.setStepId(st.getId());
        r.setMachineId(st.getMachineId());
        r.setMachineCode(st.getMachineCode());
        r.setEmployeeId(d.getEmployeeId());
        r.setEmployeeName(d.getEmployeeName());
        r.setShiftId(d.getShiftId());
        r.setShiftName(d.getShiftName());
        r.setReportType("开工");
        r.setStartTime(start);
        r.setEndTime(start);
        r.setWorkDate(start.toLocalDate());
        r.setRunMinutes(BigDecimal.ZERO);
        r.setStopMinutes(BigDecimal.ZERO);
        r.setQty(BigDecimal.ZERO);
        r.setQualifiedQty(BigDecimal.ZERO);
        r.setDefectQty(BigDecimal.ZERO);
        r.setNcProgramNo(st.getNcProgramNo());
        r.setReportTime(start);
        return r;
    }

    // ==================================================================
    //  4. 停机记录
    // ==================================================================

    private static final String[][] STOP_REASONS = {
            {"换刀", "TOOL_CHANGE", "刀具寿命到期，更换刀片并重新对刀", "非计划停机"},
            {"换型调机", "CHANGEOVER", "更换产品，重新找正夹具、试切", "非计划停机"},
            {"设备故障", "FAULT", "主轴异响报警，停机检修", "非计划停机"},
            {"等待毛坯", "WAIT_MATERIAL", "毛坯未到位，机台空等", "非计划停机"},
            {"等待程序", "WAIT_PROGRAM", "新程序未调试完成，等待工艺", "非计划停机"},
            {"首件检验", "WAIT_INSPECT", "首件送三坐标检测，等待结果", "计划停机"},
            {"计划保养", "PLAN_MAINT", "按保养计划停机维护", "计划停机"},
            {"待料停工", "IDLE", "下道工序积压，机台待机", "非计划停机"},
    };

    private void genStops(Ctx c) {
        LocalDate today = LocalDate.now();
        for (MdMachine m : c.machines) {
            if ("OFFLINE".equals(m.getRunState())) continue;
            for (int d = historyDays - 1; d >= 0; d--) {
                LocalDate day = today.minusDays(d);
                int n = ri(1, 3);
                for (int k = 0; k < n; k++) {
                    String[] rs = STOP_REASONS[rnd.nextInt(STOP_REASONS.length)];
                    LocalDateTime start = day.atTime(ri(8, 20), rnd.nextBoolean() ? 0 : 30);
                    if (d == 0 && start.isAfter(LocalDateTime.now())) continue;
                    double mins = ri(5, 140);
                    LocalDateTime end = start.plusMinutes(Math.round(mins));
                    ProdOrderStep running = c.runningSteps.stream()
                            .filter(s -> Objects.equals(s.getMachineId(), m.getId())).findFirst().orElse(null);

                    ProdStopRecord s = new ProdStopRecord();
                    s.setMachineId(m.getId());
                    s.setMachineCode(m.getMacCode());
                    s.setOrderId(running == null ? null : running.getOrderId());
                    s.setOrderNo(running == null ? null : running.getOrderNo());
                    s.setStepId(running == null ? null : running.getId());
                    s.setStopType(rs[3]);
                    s.setReasonCode(rs[1]);
                    s.setReasonDesc(rs[2]);
                    s.setStartTime(start);
                    s.setEndTime(end);
                    s.setDurationMin(bd(mins));
                    s.setHandler(rs[1].equals("FAULT") ? "赵国强" : pick(c.operators).getEmpName());
                    s.setAffectQty(bd(rnd.nextDouble() < 0.4 ? ri(1, 8) : 0));
                    s.setShiftName(hourShift(start.getHour()));
                    s.setStatus("已关闭");
                    stopMapper.insert(s);
                }
            }
        }
    }

    // ==================================================================
    //  5. 机床状态机日志
    // ==================================================================

    private void genStatusLogs(Ctx c) {
        LocalDate today = LocalDate.now();
        for (MdMachine m : c.machines) {
            if ("OFFLINE".equals(m.getRunState())) continue;
            for (int d = 6; d >= 0; d--) {
                LocalDate day = today.minusDays(d);
                LocalDateTime cursor = day.atTime(8, 0);
                LocalDateTime limit = d == 0 ? LocalDateTime.now() : day.atTime(20, 0);
                String[] plan = {"RUN", "RUN", "IDLE", "RUN", "STOP", "RUN"};
                int i = 0;
                while (cursor.isBefore(limit)) {
                    String status = plan[i++ % plan.length];
                    long sec = (long) ri(20, 150) * 60;
                    LocalDateTime end = cursor.plusSeconds(sec);
                    if (end.isAfter(limit)) end = limit;

                    ProdMachineStatusLog g = new ProdMachineStatusLog();
                    g.setMachineId(m.getId());
                    g.setMachineCode(m.getMacCode());
                    g.setStatus(status);
                    g.setPrevStatus(i > 1 ? plan[(i - 2) % plan.length] : "OFFLINE");
                    g.setReason(switch (status) {
                        case "RUN" -> "正常加工";
                        case "IDLE" -> "待机：等待派工";
                        case "STOP" -> "停机：换刀/换型";
                        default -> "报警";
                    });
                    ProdOrderStep running = c.runningSteps.stream()
                            .filter(s -> Objects.equals(s.getMachineId(), m.getId())).findFirst().orElse(null);
                    g.setOrderNo(running == null ? null : running.getOrderNo());
                    g.setNcProgramNo(running == null ? null : running.getNcProgramNo());
                    g.setStartTime(cursor);
                    g.setEndTime(end);
                    g.setDurationSec(java.time.Duration.between(cursor, end).getSeconds());
                    g.setOperator(pick(c.operators).getEmpName());
                    statusLogMapper.insert(g);
                    cursor = end;
                }
            }
        }
    }

    // ==================================================================
    //  6. 遥测历史（近 8 小时，5 分钟粒度，用于趋势图）
    // ==================================================================

    private void genTelemetryHistory(Ctx c) {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        for (MdMachine m : c.machines) {
            if ("OFFLINE".equals(m.getRunState())) continue;
            for (int i = 95; i >= 0; i--) {
                LocalDateTime ts = now.minusMinutes(i * 5L);
                telemetryMapper.insert(buildTelemetry(c, m, ts));
            }
        }
    }

    private ProdMachineTelemetry buildTelemetry(Ctx c, MdMachine m, LocalDateTime ts) {
        ProdOrderStep running = c.runningSteps.stream()
                .filter(s -> Objects.equals(s.getMachineId(), m.getId())).findFirst().orElse(null);
        boolean idle = "IDLE".equals(m.getRunState()) || "MAINT".equals(m.getStatus());
        boolean stopped = "STOP".equals(m.getRunState()) || "ALARM".equals(m.getStatus());
        String state = stopped ? "STOP" : (idle ? "IDLE" : "RUN");

        int baseSpeed = m.getMaxSpindleSpeed() == null ? 3000 : Math.max(600, m.getMaxSpindleSpeed());
        double spindle = switch (state) {
            case "RUN" -> baseSpeed * (0.45 + rnd.nextDouble() * 0.5);
            case "IDLE" -> baseSpeed * 0.02;
            default -> 0;
        };

        ProdMachineTelemetry t = new ProdMachineTelemetry();
        t.setMachineId(m.getId());
        t.setMachineCode(m.getMacCode());
        t.setTs(ts);
        t.setSpindleSpeed(bd(Math.round(spindle)));
        t.setFeedRate(bd("RUN".equals(state) ? ri(120, 2200) : 0));
        t.setFeedOverride(bd("RUN".equals(state) ? pick(List.of(80, 90, 100, 100, 110, 120)) : 0));
        t.setSpindleLoad(bd("RUN".equals(state) ? Math.round(28 + rnd.nextDouble() * 62) : Math.round(rnd.nextDouble() * 6)));
        t.setAxisX(bd(round3(rnd.nextDouble() * (m.getTravelX() == null ? 500 : m.getTravelX().doubleValue()))));
        t.setAxisY(bd(round3(rnd.nextDouble() * (m.getTravelY() == null ? 400 : m.getTravelY().doubleValue()))));
        t.setAxisZ(bd(round3(-rnd.nextDouble() * (m.getTravelZ() == null ? 300 : m.getTravelZ().doubleValue()))));
        t.setProgramNo(running == null ? null : running.getNcProgramNo());
        t.setToolNo("T" + String.format("%02d", ri(1, Math.max(2, m.getToolMagazineCapacity() == null ? 8 : Math.min(8, m.getToolMagazineCapacity())))));
        t.setAlarmCode("ALARM".equals(m.getStatus()) ? pick(List.of("SV0432", "SP1245", "OT0500", "EX1023")) : null);
        t.setRunState(state);
        t.setPartCount(ri(0, 60));
        t.setPowerKw(bd(round3("RUN".equals(state) ? 6 + rnd.nextDouble() * 18 : rnd.nextDouble() * 2.5)));
        t.setCoolantOn("RUN".equals(state) ? 1 : 0);
        return t;
    }

    // ==================================================================
    //  7. 检验单与检验项
    // ==================================================================

    private static final String[][] QC_ITEMS = {
            {"外圆直径", "DIM", "0.012", "mm"},
            {"内孔直径", "DIM", "0.015", "mm"},
            {"总长", "DIM", "0.10", "mm"},
            {"端面跳动", "GEO", "0.02", "mm"},
            {"同轴度", "GEO", "0.025", "mm"},
            {"表面粗糙度 Ra", "SURFACE", "0.4", "μm"},
            {"螺纹中径", "DIM", "0.03", "mm"},
            {"硬度", "HARDNESS", "5.0", "HRC"},
    };

    private void genInspections(Ctx c) {
        for (ProdOrderStep st : c.allSteps) {
            boolean needInsp = st.getIsInspection() == 1 || st.getIsKeyProc() == 1;
            if (!needInsp) continue;
            if ("PENDING".equals(st.getStatus()) || "DISPATCHED".equals(st.getStatus())) continue;

            int rounds = "FINISHED".equals(st.getStatus()) ? ri(1, 2) : 1;
            for (int k = 0; k < rounds; k++) {
                String type = st.getIsInspection() == 1 ? "FINAL" : (k == 0 ? "FIRST" : "PATROL");
                int sample = "FIRST".equals(type) ? 1 : ri(1, 5);
                int defect = rnd.nextDouble() < 0.14 ? ri(1, Math.max(1, sample / 2)) : 0;
                LocalDateTime time = (st.getActualStart() == null ? st.getPlanStart() : st.getActualStart())
                        .plusMinutes(ri(30, 400));

                QcInspection ins = new QcInspection();
                ins.setInspNo(CodeGen.next("QC"));
                ins.setInspType(type);
                ins.setOrderId(st.getOrderId());
                ins.setOrderNo(st.getOrderNo());
                ins.setStepId(st.getId());
                ins.setProcName(st.getProcName());
                ins.setMachineId(st.getMachineId());
                ins.setMachineCode(st.getMachineCode());
                ins.setProductName(c.orders.stream().filter(o -> o.getId().equals(st.getOrderId()))
                        .findFirst().map(ProdOrder::getProductName).orElse(null));
                ins.setSnCode(null);
                ins.setSampleQty(sample);
                ins.setQualifiedQty(sample - defect);
                ins.setDefectQty(defect);
                ins.setResult(defect == 0 ? "PASS" : (rnd.nextDouble() < 0.6 ? "FAIL" : "PASS"));
                ins.setInspector(pick(List.of("王海燕", "何静")));
                ins.setInspTime(time);
                ins.setConclusion("PASS".equals(ins.getResult()) ? "符合图纸技术要求，判定合格"
                        : "存在超差项，已开具不合格品处置单");
                inspMapper.insert(ins);

                int itemCount = ri(3, 6);
                Set<Integer> used = new HashSet<>();
                for (int j = 0; j < itemCount; j++) {
                    int idx = rnd.nextInt(QC_ITEMS.length);
                    if (!used.add(idx)) continue;
                    String[] def = QC_ITEMS[idx];
                    double nominal = switch (def[1]) {
                        case "GEO" -> 0.0;
                        case "SURFACE" -> 1.6;
                        case "HARDNESS" -> 28.0;
                        default -> ri(20, 180) + rnd.nextInt(100) / 100.0;
                    };
                    double tol = Double.parseDouble(def[2]);
                    boolean bad = defect > 0 && j == 0;
                    double value = nominal + (bad ? tol * (1.2 + rnd.nextDouble()) : (rnd.nextDouble() - 0.5) * tol * 1.2);

                    QcInspectionItem it = new QcInspectionItem();
                    it.setInspectionId(ins.getId());
                    it.setItemName(def[0]);
                    it.setItemType(def[1]);
                    it.setNominalValue(bd(nominal));
                    it.setUpperTol(bd(tol));
                    it.setLowerTol(bd(-tol));
                    it.setUnit(def[3]);
                    it.setMeasureValue(bd(Math.round(value * 1000) / 1000.0));
                    it.setResult(bad ? "FAIL" : "PASS");
                    it.setGaugeUsed(switch (def[1]) {
                        case "GEO" -> "三坐标测量机";
                        case "SURFACE" -> "粗糙度仪";
                        case "HARDNESS" -> "洛氏硬度计";
                        default -> "数显千分尺 0-25";
                    });
                    inspItemMapper.insert(it);
                }
            }
        }
    }

    // ==================================================================
    //  8. 不合格品处置（返工/返修/让步接收/报废 四态闭环）
    // ==================================================================

    private void genDefects(Ctx c) {
        List<ProdOrderStep> candidates = c.allSteps.stream()
                .filter(s -> "FINISHED".equals(s.getStatus()) || "RUNNING".equals(s.getStatus()))
                .filter(s -> s.getDefectQty() != null && s.getDefectQty().doubleValue() > 0)
                .toList();
        for (ProdOrderStep st : candidates) {
            ProdOrder o = c.orders.stream().filter(x -> x.getId().equals(st.getOrderId())).findFirst().orElse(null);
            if (o == null) continue;
            ProdTaskReport rep = c.reports.stream().filter(r -> r.getStepId().equals(st.getId())
                    && r.getDefectQty() != null && r.getDefectQty().doubleValue() > 0).findFirst().orElse(null);
            if (rep == null) continue;

            String defectType = rep.getScrapReason() == null ? "尺寸超差" : rep.getScrapReason();
            String handle = switch (defectType) {
                case "毛刺未清", "碰伤划伤" -> "返工";
                case "表面粗糙度" -> "返修";
                case "崩刃刀痕" -> "报废";
                default -> pick(List.of("返工", "返修", "让步接收", "报废"));
            };
            boolean closed = rnd.nextDouble() < 0.7;

            QcDefect d = new QcDefect();
            d.setDefectNo(CodeGen.next("NC"));
            d.setOrderId(o.getId());
            d.setOrderNo(o.getOrderNo());
            d.setStepId(st.getId());
            d.setMachineId(st.getMachineId());
            d.setMachineCode(st.getMachineCode());
            d.setMaterialId(o.getProductMatId());
            d.setProductName(o.getProductName());
            d.setDefectType(defectType);
            d.setDefectDesc(switch (defectType) {
                case "尺寸超差" -> "实测尺寸超出图纸公差带，超差 0.02~0.05mm";
                case "表面粗糙度" -> "加工面 Ra 实测 3.2 高于图纸要求 Ra1.6";
                case "碰伤划伤" -> "工件表面存在碰伤，长度约 6mm";
                case "崩刃刀痕" -> "刀具崩刃在工件表面留下明显刀痕，深度约 0.15mm";
                case "毛刺未清" -> "内孔交口处毛刺未清除干净";
                default -> "形位公差不满足图纸要求";
            });
            d.setDefectQty(rep.getDefectQty());
            d.setReasonType(switch (defectType) {
                case "崩刃刀痕" -> "刀具原因";
                case "尺寸超差" -> "设备/程序原因";
                case "毛刺未清" -> "人员原因";
                default -> "工艺原因";
            });
            d.setHandleType(handle);
            d.setHandleStatus(closed ? "已关闭" : "处理中");
            d.setHandler("王海燕");
            d.setHandleTime(closed ? rep.getEndTime().plusHours(ri(1, 20)) : null);
            d.setCostLoss(bd(Math.round(rnd.nextDouble() * 480 * 100) / 100.0));
            d.setCauseAnalysis(switch (d.getReasonType()) {
                case "刀具原因" -> "刀具寿命管理未及时预警，切削参数偏高，已要求调整寿命阈值";
                case "设备/程序原因" -> "刀补磨损未及时修正，程序版本与工艺要求不一致，已修订程序";
                case "人员原因" -> "操作工未按作业指导书执行去毛刺工步，已安排培训";
                default -> "装夹定位面有铁屑，导致定位偏移，已增加吹屑工步";
            });
            defectMapper.insert(d);
        }
    }

    // ==================================================================
    //  9. SPC 采样
    // ==================================================================

    private void genSpc(Ctx c) {
        List<MdMachine> targets = c.machines.stream()
                .filter(m -> List.of("数控车床", "车削中心", "外圆磨床", "立式加工中心").contains(m.getCncType()))
                .limit(4).toList();
        for (MdMachine m : targets) {
            String charName = m.getCncType().contains("磨") ? "外圆直径 φ45 (0/-0.02)" : "外圆直径 φ45 (0/-0.025)";
            double nominal = 45.0;
            double tol = m.getCncType().contains("磨") ? 0.02 : 0.025;
            double drift = 0;
            for (int i = 1; i <= 25; i++) {
                drift += (rnd.nextDouble() - 0.5) * tol * 0.25;
                drift *= 0.85;
                double v = nominal - tol / 2 + drift + (rnd.nextDouble() - 0.5) * tol * 0.7;

                QcSpcSample s = new QcSpcSample();
                s.setCharName(charName);
                s.setCharCode("D45");
                s.setMachineId(m.getId());
                s.setMachineCode(m.getMacCode());
                s.setNominalValue(bd(nominal));
                s.setUpperLimit(bd(nominal));
                s.setLowerLimit(bd(nominal - tol));
                s.setSampleValue(bd(Math.round(v * 10000) / 10000.0));
                s.setSampleNo(i);
                s.setSampleTime(LocalDateTime.now().minusHours(25L - i));
                s.setInspector(pick(List.of("王海燕", "何静")));
                spcMapper.insert(s);
            }
        }
    }

    // ==================================================================
    //  10. 追溯（序列号 + 过程记录）
    // ==================================================================

    private void genTrace(Ctx c) {
        for (ProdOrder o : c.orders) {
            if ("RELEASED".equals(o.getStatus()) || "SCHEDULED".equals(o.getStatus())) continue;
            List<ProdOrderStep> steps = c.allSteps.stream().filter(s -> s.getOrderId().equals(o.getId())).toList();
            /*
             * 序列号数量按「计划数量」而不是「已完工数量」生成。
             * 原来用 finishedQty：在制工单的完工数还是 0，于是每张在制工单只有 1 个序列号，
             * 「同一工单多件 → 多序列号 → 可反查」这条最需要演示的能力恰好看不到。
             */
            int snCount = (int) Math.min(8, Math.max(1,
                    o.getPlanQty() == null ? 1 : o.getPlanQty().doubleValue()));
            String batchNo = c.batches.isEmpty() ? null : c.batches.get((int) (o.getId() % c.batches.size())).getBatchNo();

            for (int i = 1; i <= snCount; i++) {
                String sn = o.getProductCode() + "-" + o.getOrderNo().substring(o.getOrderNo().length() - 6) + "-" + String.format("%03d", i);
                TraceSn t = new TraceSn();
                t.setSnCode(sn);
                t.setOrderId(o.getId());
                t.setOrderNo(o.getOrderNo());
                t.setMaterialId(o.getProductMatId());
                t.setProductCode(o.getProductCode());
                t.setProductName(o.getProductName());
                t.setBatchNo(batchNo);
                t.setStatus("FINISHED".equals(o.getStatus()) ? "完工" : "在制");
                t.setQcResult(rnd.nextDouble() < 0.9 ? "合格" : "待复检");
                t.setCreateTime(o.getPlanStart());
                if ("FINISHED".equals(o.getStatus())) t.setCompleteTime(o.getPlanEnd());
                snMapper.insert(t);

                List<ProdOrderStep> done = steps.stream()
                        .filter(s -> !"PENDING".equals(s.getStatus()) && !"DISPATCHED".equals(s.getStatus())).toList();
                for (ProdOrderStep st : done) {
                    ProdDispatch d = c.dispatchByStep.get(st.getId());
                    TraceLog tl = new TraceLog();
                    tl.setSnCode(sn);
                    tl.setOrderId(o.getId());
                    tl.setOrderNo(o.getOrderNo());
                    tl.setStepId(st.getId());
                    tl.setSeqNo(st.getSeqNo());
                    tl.setProcName(st.getProcName());
                    tl.setMachineId(st.getMachineId());
                    tl.setMachineCode(st.getMachineCode());
                    tl.setEmployeeId(d == null ? null : d.getEmployeeId());
                    tl.setOperator(d == null ? null : d.getEmployeeName());
                    tl.setNcProgramNo(st.getNcProgramNo());
                    ToolLedger tool = c.tools.stream().filter(x -> Objects.equals(x.getMachineId(), st.getMachineId())).findFirst().orElse(null);
                    tl.setToolCode(tool == null ? null : tool.getToolCode());
                    tl.setToolOffsetNo(tool == null ? null : tool.getOffsetNo());
                    tl.setMaterialBatch(batchNo);
                    tl.setStartTime(st.getActualStart() == null ? st.getPlanStart() : st.getActualStart());
                    tl.setEndTime(st.getActualEnd() == null ? st.getPlanEnd() : st.getActualEnd());
                    tl.setQty(BigDecimal.ONE);
                    tl.setRunMinutes(st.getPieceTime() == null ? null : st.getPieceTime());
                    tl.setQcResult(rnd.nextDouble() < 0.93 ? "合格" : "待复检");
                    tl.setShiftName(hourShift(tl.getStartTime().getHour()));
                    tl.setParamJson("{\"主轴转速\":" + ri(1200, 6800) + ",\"进给\":" + ri(180, 1600)
                            + ",\"切深\":" + (Math.round(rnd.nextDouble() * 20) / 10.0) + ",\"冷却\":true}");
                    traceLogMapper.insert(tl);
                }
            }
        }
    }

    // ==================================================================
    //  11. 刀具使用 / 刃磨 / 对刀
    // ==================================================================

    private void genToolRecords(Ctx c) {
        for (ToolLedger tool : c.tools) {
            if (tool.getMachineId() == null) continue;
            List<ProdTaskReport> used = c.reports.stream()
                    .filter(r -> Objects.equals(r.getMachineId(), tool.getMachineId())).toList();
            if (used.isEmpty()) continue;

            LocalDateTime cursor = used.get(0).getStartTime();
            int idx = 0;
            while (idx < used.size()) {
                int take = Math.min(used.size() - idx, ri(2, 6));
                List<ProdTaskReport> group = used.subList(idx, idx + take);
                double mins = group.stream().mapToDouble(r -> r.getRunMinutes().doubleValue()).sum();
                int parts = (int) group.stream().mapToDouble(r -> r.getQty().doubleValue()).sum();

                ToolUsage u = new ToolUsage();
                u.setToolId(tool.getId());
                u.setToolCode(tool.getToolCode());
                u.setToolName(tool.getToolName());
                u.setMachineId(tool.getMachineId());
                u.setMachineCode(tool.getMachineCode());
                u.setOrderId(group.get(0).getOrderId());
                u.setOrderNo(group.get(0).getOrderNo());
                u.setStepId(group.get(0).getStepId());
                u.setNcProgramNo(group.get(0).getNcProgramNo());
                u.setOffsetNo(tool.getOffsetNo());
                u.setMountTime(cursor);
                u.setUnmountTime(group.get(group.size() - 1).getEndTime());
                u.setUseMinutes(bd(Math.round(mins)));
                u.setPartQty(parts);
                u.setWearValue(bd(Math.round(rnd.nextDouble() * 0.28 * 10000) / 10000.0));
                u.setUnmountReason(pick(List.of("寿命到期", "换型", "计划换刀", "加工质量异常")));
                u.setOperator(group.get(0).getEmployeeName());
                toolUsageMapper.insert(u);
                cursor = u.getUnmountTime().plusHours(ri(4, 30));
                idx += take;
            }

            if ("待刃磨".equals(tool.getStatus())) {
                ToolGrind g = new ToolGrind();
                g.setToolId(tool.getId());
                g.setToolCode(tool.getToolCode());
                g.setGrindNo(CodeGen.next("TG"));
                g.setGrindType("刃磨");
                g.setBeforeSpec(tool.getSpec());
                g.setAfterSpec(tool.getSpec().replace("φ", "φ") + "(刃磨后)");
                g.setGrindAmount(bd(Math.round(rnd.nextDouble() * 0.12 * 1000) / 1000.0));
                g.setGrindTime(LocalDateTime.now().minusDays(ri(1, 6)));
                g.setGrinder("陈立");
                g.setResult("合格");
                g.setCost(bd(Math.round(tool.getUnitPrice().doubleValue() * 0.25 * 100) / 100.0));
                g.setRemark("刃磨后直径减小，需重新对刀");
                toolGrindMapper.insert(g);
            }

            if (rnd.nextDouble() < 0.6) {
                ToolMeasure m = new ToolMeasure();
                m.setToolId(tool.getId());
                m.setToolCode(tool.getToolCode());
                m.setMachineId(tool.getMachineId());
                m.setMachineCode(tool.getMachineCode());
                m.setOffsetNo(tool.getOffsetNo());
                m.setRadiusValue(bd(Math.round((2 + rnd.nextDouble() * 6) * 1000) / 1000.0));
                m.setLengthValue(bd(Math.round((60 + rnd.nextDouble() * 90) * 1000) / 1000.0));
                m.setDeviation(bd(Math.round((rnd.nextDouble() - 0.5) * 0.02 * 10000) / 10000.0));
                m.setMeasureTime(LocalDateTime.now().minusDays(ri(1, 8)));
                m.setMeasurer("陈立");
                m.setResult("合格");
                toolMeasureMapper.insert(m);
            }
        }
    }

    // ==================================================================
    //  12. 设备点检 / 故障 / 保养
    // ==================================================================

    private void genEquipmentRecords(Ctx c) {
        LocalDate today = LocalDate.now();
        for (MdMachine m : c.machines) {
            for (int d = 6; d >= 0; d--) {
                LocalDate day = today.minusDays(d);
                for (EqCheckItem item : c.checkItems) {
                    if (!"ALL".equals(item.getMacType()) && !item.getMacType().equals(m.getCncType())) continue;
                    if ("每周".equals(item.getPeriodType()) && day.getDayOfWeek().getValue() != 1) continue;
                    boolean abnormal = item.getIsKey() == 1 && rnd.nextDouble() < 0.045;
                    EqCheckRecord r = new EqCheckRecord();
                    r.setMachineId(m.getId());
                    r.setMachineCode(m.getMacCode());
                    r.setItemId(item.getId());
                    r.setItemName(item.getItemName());
                    r.setCheckTime(day.atTime(7, ri(30, 59)));
                    r.setChecker(pick(List.of("赵国强", "马晓东")));
                    r.setShiftName("白班");
                    r.setResult(abnormal ? "异常" : "正常");
                    if (abnormal) {
                        r.setAbnormalDesc(pick(List.of("气压低于 0.45MPa，影响夹紧", "主轴温升偏高，达 42K",
                                "切削液浓度 4.2%，低于下限", "导轨润滑不足，出现轻微干涩")));
                        r.setHandleStatus(rnd.nextDouble() < 0.6 ? "已处理" : "待处理");
                        if ("已处理".equals(r.getHandleStatus())) r.setHandleTime(r.getCheckTime().plusHours(ri(1, 6)));
                    }
                    checkRecordMapper.insert(r);
                }
            }
        }

        String[] faultTypes = {"机械", "电气", "数控系统", "液压", "气动", "刀具干涉"};
        for (int i = 0; i < 16; i++) {
            MdMachine m = c.machines.get(rnd.nextInt(c.machines.size()));
            String type = faultTypes[rnd.nextInt(faultTypes.length)];
            LocalDateTime report = LocalDateTime.now().minusDays(ri(0, 13)).withHour(ri(8, 20)).withMinute(ri(0, 59));
            LocalDateTime respond = report.plusMinutes(ri(10, 90));
            boolean closed = rnd.nextDouble() < 0.8;
            LocalDateTime end = respond.plusMinutes(ri(45, 600));

            EqFault f = new EqFault();
            f.setFaultNo(CodeGen.next("FT"));
            f.setMachineId(m.getId());
            f.setMachineCode(m.getMacCode());
            f.setFaultType(type);
            f.setFaultCode(switch (type) {
                case "数控系统" -> pick(List.of("SV0432", "SP1245", "OT0500"));
                case "电气" -> pick(List.of("ALM401", "ALM120"));
                case "液压" -> "HYD-01";
                default -> null;
            });
            f.setFaultDesc(switch (type) {
                case "机械" -> "X 轴丝杠异响，定位精度下降";
                case "电气" -> "主轴驱动器报警，无法启动主轴";
                case "数控系统" -> "系统伺服报警 SV0432，轴过载";
                case "液压" -> "液压站压力不足，卡盘夹紧力不够";
                case "气动" -> "气源压力波动，换刀动作中断";
                default -> "换刀过程中刀库与主轴干涉，自动停机";
            });
            f.setReportUser(pick(c.operators).getEmpName());
            f.setReportTime(report);
            f.setRespondTime(respond);
            f.setRepairEndTime(closed ? end : null);
            f.setDownMinutes(bd(closed ? java.time.Duration.between(report, end).toMinutes() : 0));
            f.setCause(switch (type) {
                case "机械" -> "丝杠预紧力不足，轴承磨损";
                case "电气" -> "驱动器散热不良导致过热保护";
                case "数控系统" -> "伺服负载过大，参数不匹配";
                case "液压" -> "液压油污染，溢流阀卡滞";
                case "气动" -> "三联件滤芯堵塞";
                default -> "刀库定位偏差，接近开关失效";
            });
            f.setSolution(switch (type) {
                case "机械" -> "重新预紧丝杠，更换角接触轴承，重新检测定位精度";
                case "电气" -> "清理驱动器散热风道，更换风扇";
                case "数控系统" -> "优化加减速参数，重做伺服增益调整";
                case "液压" -> "更换液压油与溢流阀，清洗油箱";
                case "气动" -> "更换三联件滤芯并调整压力";
                default -> "调整刀库定位，更换接近开关";
            });
            f.setSpareParts(switch (type) {
                case "机械" -> "角接触轴承 7205 ×2";
                case "电气" -> "驱动器风扇 ×2";
                case "液压" -> "溢流阀 ×1、46#液压油 20L";
                case "气动" -> "三联件滤芯 ×1";
                default -> "接近开关 ×2";
            });
            f.setRepairUser(pick(List.of("赵国强", "马晓东")));
            f.setStatus(closed ? "已修复" : (rnd.nextBoolean() ? "维修中" : "待维修"));
            faultMapper.insert(f);
        }

        for (EqMaintPlan p : c.maintPlans) {
            boolean done = !"逾期".equals(p.getStatus());
            EqMaintOrder o = new EqMaintOrder();
            o.setMaintNo(CodeGen.next("MT"));
            o.setPlanId(p.getId());
            o.setMachineId(p.getMachineId());
            o.setMachineCode(p.getMachineCode());
            o.setMaintType(p.getMaintType());
            o.setFaultDesc(done ? null : "计划已逾期未执行，设备仍在使用");
            o.setMaintainer(p.getPrincipal());
            o.setPlanStart(p.getNextDate() == null ? null : p.getNextDate().atTime(8, 30));
            o.setPlanEnd(p.getNextDate() == null ? null : p.getNextDate().atTime(16, 30));
            if (done) {
                o.setActualStart(o.getPlanStart());
                o.setActualEnd(o.getPlanStart().plusHours(ri(3, 7)));
                o.setDownMinutes(bd(java.time.Duration.between(o.getActualStart(), o.getActualEnd()).toMinutes()));
                o.setResult("按保养项目执行完成，设备精度校验合格");
                o.setStatus("已完成");
                o.setCost(bd(ri(200, 2600)));
            } else {
                o.setStatus("待执行");
            }
            o.setSpareParts(p.getContent());
            maintOrderMapper.insert(o);
        }
    }

    // ==================================================================
    //  13. 安灯呼叫
    // ==================================================================

    private void genAndon(Ctx c) {
        String[][] andons = {
                {"质量异常", "一级", "首件检验尺寸超差，等待质检确认"},
                {"质量异常", "二级", "连续 3 件外观碰伤，怀疑来料问题"},
                {"设备异常", "一级", "主轴异响，请求设备到场确认"},
                {"设备异常", "二级", "刀库换刀卡滞，机台停机"},
                {"物料短缺", "一级", "毛坯剩余不足，需补料 30 件"},
                {"工艺求助", "二级", "新程序试切振纹明显，请求工艺调整参数"},
                {"刀具需求", "一级", "φ12 立铣刀寿命到期，需配送新刀"},
                {"刀具需求", "二级", "刀具库缺 M12 丝锥，影响后续工单"},
        };
        for (int i = 0; i < 28; i++) {
            String[] a = andons[i % andons.length];
            ProdOrderStep st = c.runningSteps.isEmpty() ? null : c.runningSteps.get(i % c.runningSteps.size());
            LocalDateTime call = LocalDateTime.now().minusHours(ri(1, historyDays * 12));
            boolean closed = rnd.nextDouble() < 0.75;
            LocalDateTime resp = call.plusMinutes(ri(2, 45));
            LocalDateTime done = resp.plusMinutes(ri(10, 240));

            ProdAndon o = new ProdAndon();
            o.setAndonNo(CodeGen.next("AN"));
            o.setMachineId(st == null ? null : st.getMachineId());
            o.setMachineCode(st == null ? null : st.getMachineCode());
            o.setOrderId(st == null ? null : st.getOrderId());
            o.setOrderNo(st == null ? null : st.getOrderNo());
            o.setAndonType(a[0]);
            o.setLevel(a[1]);
            o.setContent(a[2]);
            o.setCallUser(pick(c.operators).getEmpName());
            o.setCallTime(call);
            o.setRespondUser(pick(List.of("蔡明", "高翔", "王海燕", "赵国强", "陈立")));
            o.setRespondTime(resp);
            o.setResolveTime(closed ? done : null);
            o.setDurationMin(closed ? bd(java.time.Duration.between(call, done).toMinutes()) : null);
            o.setStatus(closed ? "已关闭" : (rnd.nextDouble() < 0.7 ? "处理中" : "待响应"));
            andonMapper.insert(o);
        }
    }

    // ==================================================================
    //  工具方法
    // ==================================================================

    private int ri(int min, int max) {
        return min + rnd.nextInt(Math.max(1, max - min + 1));
    }

    private double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private <T> T pick(List<T> list) {
        return list.get(rnd.nextInt(list.size()));
    }

    private String hourShift(int hour) {
        if (hour >= 8 && hour < 17) return "白班";
        if (hour >= 17 || hour < 1) return "中班";
        return "夜班";
    }
}
