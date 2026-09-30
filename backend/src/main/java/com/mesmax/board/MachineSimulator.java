package com.mesmax.board;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.entity.MdMachine;
import com.mesmax.entity.MdWorkCenter;
import com.mesmax.entity.ProdOrder;
import com.mesmax.entity.ProdOrderStep;
import com.mesmax.entity.ProdMachineTelemetry;
import com.mesmax.mapper.MdMachineMapper;
import com.mesmax.mapper.MdWorkCenterMapper;
import com.mesmax.mapper.ProdMachineTelemetryMapper;
import com.mesmax.mapper.ProdOrderMapper;
import com.mesmax.mapper.ProdOrderStepMapper;
import com.mesmax.mapper.ToolLedgerMapper;
import com.mesmax.entity.ToolLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 设备数据采集模拟器。
 *
 * <p><b>重要说明</b>：真实产线中，机床数据由 DNC / FOCAS / OPC UA / MTConnect 网关采集后写入
 * {@code prod_machine_telemetry}，本类只是「没有真实机床时的替身」。它复刻了 CNC 的真实行为特征：
 * <ul>
 *   <li>主轴转速随加工状态在额定转速的 45%~95% 之间波动，待机时接近 0</li>
 *   <li>主轴负载在 25%~90% 之间波动，粗加工区间更高</li>
 *   <li>进给倍率常见档位 80/90/100/110/120</li>
 *   <li>各轴坐标持续变化，程序号取自当前在制的工序任务</li>
 *   <li>状态机在 运行 / 待机 / 停机 / 报警 之间跃迁，报警带 FANUC 风格报警码</li>
 * </ul>
 * 接入真机时，只需保留 {@link MachineStateRegistry} 与 SSE 推送，把本类的 {@code tick()} 换成网关写入即可。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MachineSimulator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MachineSimulator.class);

    /** FANUC / 西门子常见的报警码，用于模拟真实报警。 */
    private static final String[] ALARM_CODES = {"SV0432", "SP1245", "OT0500", "EX1023", "ALM401", "DRV-07"};
    private static final String[] ALARM_TEXT = {
            "伺服轴过载", "主轴驱动器过热", "行程超限", "程序格式错误", "放大器报警", "驱动器通讯异常"};

    private final MdMachineMapper machineMapper;
    private final MdWorkCenterMapper workCenterMapper;
    private final ProdOrderMapper orderMapper;
    private final ProdOrderStepMapper stepMapper;
    private final ProdMachineTelemetryMapper telemetryMapper;
    private final ToolLedgerMapper toolMapper;
    private final MachineStateRegistry registry;
    private final SseService sseService;

    private final Random rnd = new Random();

    /** 当前在制的工序任务（机台 → 任务），每 30 秒刷新一次。 */
    private volatile Map<Long, ProdOrderStep> runningByMachine = Map.of();
    private volatile Map<Long, ProdOrder> orderById = Map.of();
    private volatile Map<Long, String> workCenterName = Map.of();
    /**
     * 机台主数据与「机台 → 在用刀具」快照，随上下文一起每 30 秒刷新。
     *
     * <p>采集循环是每 2 秒一轮的热路径：原来每轮对每台机床各做一次
     * {@code machineMapper.selectById} 和一次刀具查询，12 台机就是每 2 秒约 24 次查询，
     * 而这些数据（额定转速、行程、刀具号）几乎不变。改成快照后，采集循环只剩内存读取。
     */
    private volatile Map<Long, MdMachine> machineById = Map.of();
    private volatile Map<Long, ToolLedger> toolByMachine = Map.of();

    @Value("${mes-max.simulator.enabled:true}")
    private boolean enabled;

    /** 采集器是否已完成初始化。定时任务可能早于 ApplicationRunner 触发，用此标志兜底。 */
    private volatile boolean ready = false;
    private int tickCount = 0;

    public MachineSimulator(MdMachineMapper machineMapper, MdWorkCenterMapper workCenterMapper,
                            ProdOrderMapper orderMapper, ProdOrderStepMapper stepMapper,
                            ProdMachineTelemetryMapper telemetryMapper, ToolLedgerMapper toolMapper,
                            MachineStateRegistry registry, SseService sseService) {
        this.machineMapper = machineMapper;
        this.workCenterMapper = workCenterMapper;
        this.orderMapper = orderMapper;
        this.stepMapper = stepMapper;
        this.telemetryMapper = telemetryMapper;
        this.toolMapper = toolMapper;
        this.registry = registry;
        this.sseService = sseService;
    }

    /**
     * 采集器初始化。
     *
     * <p>刻意放在 ApplicationRunner 而不是 {@code @PostConstruct}：
     * 建表与演示数据由更早的 ApplicationRunner 完成，{@code @PostConstruct} 阶段数据库里还没有表。
     */
    @Override
    public void run(ApplicationArguments args) {
        init();
    }

    public void init() {
        if (!enabled) {
            log.info("设备采集模拟器已关闭（mes-max.simulator.enabled=false）");
            return;
        }
        refreshContext();
        for (MdMachine m : machineMapper.selectList(new QueryWrapper<MdMachine>().orderByAsc("id"))) {
            LiveState s = new LiveState();
            s.setMachineId(m.getId());
            s.setMachineCode(m.getMacCode());
            s.setMachineName(m.getMacName());
            s.setCncType(m.getCncType());
            s.setWorkCenterId(m.getWorkCenterId());
            s.setWorkCenterName(workCenterName.get(m.getWorkCenterId()));
            s.setStatus(m.getRunState() == null ? "OFFLINE" : m.getRunState());
            s.setStatusSince(LocalDateTime.now());
            s.setStatusReason("采集器初始化");
            s.setOperator(null);
            s.setPartCount(0);
            s.setPlanQty(0);
            s.setFinishedQty(0);
            s.setTs(LocalDateTime.now());
            registry.put(s);
        }
        log.info("设备采集模拟器已启动，纳管机床 {} 台", registry.all().size());
        ready = true;
    }

    private void refreshContext() {
        Map<Long, ProdOrderStep> byMachine = new HashMap<>();
        List<ProdOrderStep> candidates = stepMapper.selectList(new QueryWrapper<ProdOrderStep>()
                .in("status", List.of("RUNNING", "INSPECTING", "DISPATCHED"))
                .isNotNull("machine_id").orderByAsc("plan_start"));

        // 两轮装配：优先「正在加工/检验中」的工序，其次才是已派工未开工的工序；
        // 且优先选择带 NC 程序号的工序 —— 机台卡上显示程序号才有现场意义
        // （下料、去毛刺、终检这类手工工位本身没有程序号）。
        for (ProdOrderStep s : candidates) {
            if (("RUNNING".equals(s.getStatus()) || "INSPECTING".equals(s.getStatus()))
                    && s.getNcProgramNo() != null) {
                byMachine.putIfAbsent(s.getMachineId(), s);
            }
        }
        for (ProdOrderStep s : candidates) {
            if (s.getNcProgramNo() != null) byMachine.putIfAbsent(s.getMachineId(), s);
        }
        for (ProdOrderStep s : candidates) {
            byMachine.putIfAbsent(s.getMachineId(), s);
        }
        runningByMachine = byMachine;
        Map<Long, ProdOrder> orders = new HashMap<>();
        for (ProdOrder o : orderMapper.selectList(null)) orders.put(o.getId(), o);
        orderById = orders;
        Map<Long, String> wc = new HashMap<>();
        for (MdWorkCenter w : workCenterMapper.selectList(null)) wc.put(w.getId(), w.getWcName());
        workCenterName = wc;

        Map<Long, MdMachine> machines = new HashMap<>();
        for (MdMachine m : machineMapper.selectList(null)) machines.put(m.getId(), m);
        machineById = machines;

        Map<Long, ToolLedger> tools = new HashMap<>();
        for (ToolLedger t : toolMapper.selectList(new QueryWrapper<ToolLedger>().isNotNull("machine_id"))) {
            tools.putIfAbsent(t.getMachineId(), t);
        }
        toolByMachine = tools;
    }

    /** 每 30 秒刷新一次上下文（在制工单 / 工作中心）。 */
    @Scheduled(fixedRate = 30000)
    public void scheduledRefresh() {
        if (!enabled || !ready) return;
        try {
            refreshContext();
        } catch (Exception e) {
            log.debug("刷新采集上下文失败：{}", e.getMessage());
        }
    }

    /** 采集主循环：默认 2 秒一个周期，与真机 PLC 扫描周期的量级一致。 */
    @Scheduled(fixedRateString = "${mes-max.simulator.interval-ms:2000}")
    public void tick() {
        if (!enabled || !ready) return;
        tickCount++;
        List<Map<String, Object>> payload = new ArrayList<>();

        for (LiveState s : registry.all()) {
            if ("OFFLINE".equals(s.getStatus()) && rnd.nextDouble() > 0.002) {
                payload.add(compact(s));
                continue;
            }
            transition(s);
            fillTelemetry(s);
            payload.add(compact(s));
        }

        sseService.publish("machines", payload);

        // 每 15 个周期（约 30 秒）落库一次遥测；2 秒粒度全量落库对中小车间没有必要
        if (tickCount % 15 == 0) persistTelemetry();
    }

    /** 机床状态机跃迁。概率按「一个班次内发生的次数」反推，保证看板上的状态变化不突兀也不呆板。 */
    private void transition(LiveState s) {
        String st = s.getStatus() == null ? "OFFLINE" : s.getStatus();
        ProdOrderStep task = runningByMachine.get(s.getMachineId());
        double r = rnd.nextDouble();

        switch (st) {
            case "RUN" -> {
                if (r < 0.0015) setStatus(s, "ALARM", pickAlarm());
                else if (r < 0.006) setStatus(s, "IDLE", "等待派工 / 待料");
                else if (r < 0.0075) setStatus(s, "STOP", "换刀或换型调机");
            }
            case "IDLE" -> {
                // 注意：这里必须嵌套判断，不能写成 `task != null && r < 0.06` 的 else-if 链 ——
                // 那样在「无待加工任务」时条件不成立，会穿透到下一个分支，把待机误判成报警。
                if (r < 0.06) {
                    if (task != null) setStatus(s, "RUN", "开工：" + task.getOrderNo());
                } else if (r < 0.062) {
                    setStatus(s, "ALARM", pickAlarm());
                } else if (r < 0.065) {
                    setStatus(s, "STOP", "计划保养");
                }
            }
            case "STOP" -> {
                if (r < 0.03) setStatus(s, "IDLE", "停机结束，待机");
            }
            case "ALARM" -> {
                if (r < 0.05) setStatus(s, "IDLE", "报警已清除");
            }
            case "MAINT" -> {
                if (r < 0.01) setStatus(s, "IDLE", "保养完成");
            }
            case "OFFLINE" -> {
                if (r < 0.01) setStatus(s, "IDLE", "通讯恢复");
            }
            default -> setStatus(s, "IDLE", "状态未知，已复位为待机");
        }
    }

    private String pickAlarm() {
        int i = rnd.nextInt(ALARM_CODES.length);
        return ALARM_CODES[i] + " " + ALARM_TEXT[i];
    }

    private void setStatus(LiveState s, String status, String reason) {
        if (Objects.equals(s.getStatus(), status)) return;
        String prev = s.getStatus();
        s.setStatus(status);
        s.setStatusSince(LocalDateTime.now());
        s.setStatusReason(reason);
        if ("ALARM".equals(status)) {
            s.setAlarmCode(reason);
            registry.addAlarm(Map.of(
                    "machineId", s.getMachineId(),
                    "machineCode", s.getMachineCode(),
                    "alarmCode", reason,
                    "orderNo", s.getOrderNo() == null ? "" : s.getOrderNo(),
                    "time", LocalDateTime.now().toString(),
                    "level", "二级"));
        } else {
            s.setAlarmCode(null);
        }
        // 状态跃迁落库（md_machine 的当前状态 + 状态日志）
        MdMachine m = machineMapper.selectById(s.getMachineId());
        if (m != null) {
            m.setRunState(status);
            m.setStatus(status);
            machineMapper.updateById(m);
        }
        s.setStatusReason(reason + (prev == null ? "" : "（原状态 " + prev + "）"));
    }

    /** 生成遥测值。不同 CNC 类型的转速与负载特性不同，这里做了区分。 */
    private void fillTelemetry(LiveState s) {
        // 读主数据走快照，避免 2 秒一轮的采集循环把数据库打满（见 machineById 的说明）
        MdMachine m = machineById.get(s.getMachineId());
        ProdOrderStep task = runningByMachine.get(s.getMachineId());
        boolean running = "RUN".equals(s.getStatus());
        boolean idle = "IDLE".equals(s.getStatus()) || "MAINT".equals(s.getStatus());

        int rated = m == null || m.getMaxSpindleSpeed() == null ? 6000 : Math.max(600, m.getMaxSpindleSpeed());
        double speed;
        double feed;
        double load;
        if (running) {
            speed = rated * (0.45 + rnd.nextDouble() * 0.5);
            feed = 120 + rnd.nextDouble() * 1800;
            load = 25 + rnd.nextDouble() * 62;
        } else if (idle) {
            speed = rated * rnd.nextDouble() * 0.03;
            feed = 0;
            load = rnd.nextDouble() * 6;
        } else {
            speed = 0;
            feed = 0;
            load = 0;
        }

        s.setSpindleSpeed(bd(Math.round(speed)));
        s.setFeedRate(bd(Math.round(feed)));
        s.setFeedOverride(bd(running ? pickOverride() : 0));
        s.setSpindleLoad(bd(round1(load)));
        double tx = m == null || m.getTravelX() == null ? 500 : m.getTravelX().doubleValue();
        double ty = m == null || m.getTravelY() == null ? 400 : m.getTravelY().doubleValue();
        double tz = m == null || m.getTravelZ() == null ? 300 : m.getTravelZ().doubleValue();
        s.setAxisX(bd(round3(rnd.nextDouble() * tx)));
        s.setAxisY(bd(round3(rnd.nextDouble() * ty)));
        s.setAxisZ(bd(round3(-rnd.nextDouble() * tz)));
        s.setPowerKw(bd(round2(running ? 6 + rnd.nextDouble() * 18 : rnd.nextDouble() * 2.5)));
        s.setCoolantOn(bd(running ? 1 : 0));
        s.setTs(LocalDateTime.now());

        if (task != null) {
            s.setOrderNo(task.getOrderNo());
            s.setProcName(task.getProcName());
            s.setProgramNo(task.getNcProgramNo());
            s.setPlanQty(task.getPlanQty() == null ? 0 : task.getPlanQty().intValue());
            ProdOrder o = orderById.get(task.getOrderId());
            s.setProductName(o == null ? null : o.getProductName());
        } else {
            s.setProgramNo(null);
            s.setProcName(null);
        }
        if (m != null && m.getToolMagazineCapacity() != null && m.getToolMagazineCapacity() > 0) {
            s.setToolNo("T" + String.format("%02d", rnd.nextInt(Math.min(12, m.getToolMagazineCapacity())) + 1));
        }
        if (running && rnd.nextDouble() < 0.12) {
            s.setPartCount(s.getPartCount() == null ? 1 : s.getPartCount() + 1);
            s.setFinishedQty(task != null && task.getFinishedQty() != null
                    ? task.getFinishedQty().intValue() : s.getFinishedQty());
        }
        ToolLedger tool = toolByMachine.get(s.getMachineId());
        if (tool != null) {
            s.setToolNo(tool.getOffsetNo() == null ? s.getToolNo() : tool.getOffsetNo());
        }

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("t", s.getTs().toLocalTime().withNano(0).toString());
        point.put("speed", s.getSpindleSpeed());
        point.put("feed", s.getFeedRate());
        point.put("load", s.getSpindleLoad());
        point.put("power", s.getPowerKw());
        s.push(point);
    }

    private Map<String, Object> compact(LiveState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("machineId", s.getMachineId());
        m.put("machineCode", s.getMachineCode());
        m.put("machineName", s.getMachineName());
        m.put("cncType", s.getCncType());
        m.put("workCenterName", s.getWorkCenterName());
        m.put("status", s.getStatus());
        m.put("statusReason", s.getStatusReason());
        m.put("statusMinutes", s.getStatusSince() == null ? 0
                : Duration.between(s.getStatusSince(), LocalDateTime.now()).toMinutes());
        m.put("orderNo", s.getOrderNo());
        m.put("productName", s.getProductName());
        m.put("procName", s.getProcName());
        m.put("programNo", s.getProgramNo());
        m.put("toolNo", s.getToolNo());
        m.put("alarmCode", s.getAlarmCode());
        m.put("spindleSpeed", s.getSpindleSpeed());
        m.put("feedRate", s.getFeedRate());
        m.put("feedOverride", s.getFeedOverride());
        m.put("spindleLoad", s.getSpindleLoad());
        m.put("axisX", s.getAxisX());
        m.put("axisY", s.getAxisY());
        m.put("axisZ", s.getAxisZ());
        m.put("powerKw", s.getPowerKw());
        m.put("partCount", s.getPartCount());
        m.put("planQty", s.getPlanQty());
        m.put("finishedQty", s.getFinishedQty());
        m.put("ts", s.getTs() == null ? null : s.getTs().toString());
        return m;
    }

    /** 遥测落库（每 30 秒一台机一条）。 */
    private void persistTelemetry() {
        LocalDateTime now = LocalDateTime.now();
        for (LiveState s : registry.all()) {
            if ("OFFLINE".equals(s.getStatus())) continue;
            ProdMachineTelemetry t = new ProdMachineTelemetry();
            t.setMachineId(s.getMachineId());
            t.setMachineCode(s.getMachineCode());
            t.setTs(now);
            t.setSpindleSpeed(s.getSpindleSpeed());
            t.setFeedRate(s.getFeedRate());
            t.setFeedOverride(s.getFeedOverride());
            t.setSpindleLoad(s.getSpindleLoad());
            t.setAxisX(s.getAxisX());
            t.setAxisY(s.getAxisY());
            t.setAxisZ(s.getAxisZ());
            t.setProgramNo(s.getProgramNo());
            t.setToolNo(s.getToolNo());
            t.setAlarmCode(s.getAlarmCode());
            t.setRunState(s.getStatus());
            t.setPartCount(s.getPartCount());
            t.setPowerKw(s.getPowerKw());
            t.setCoolantOn(s.getCoolantOn() == null ? 0 : s.getCoolantOn().intValue());
            try {
                telemetryMapper.insert(t);
            } catch (Exception e) {
                log.debug("遥测落库失败：{}", e.getMessage());
            }
        }
    }

    private <T> T pick(T[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }

    /** 进给倍率档位：现场常见 80/90/100/110/120，其中 100% 权重最高。 */
    private double pickOverride() {
        double[] levels = {80, 90, 100, 100, 100, 110, 120};
        return levels[rnd.nextInt(levels.length)];
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
