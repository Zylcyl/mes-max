package com.mesmax.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.BizException;
import com.mesmax.common.R;
import com.mesmax.entity.MdEmployee;
import com.mesmax.entity.MdMachine;
import com.mesmax.entity.ProdDispatch;
import com.mesmax.entity.ProdOrder;
import com.mesmax.entity.ProdOrderStep;
import com.mesmax.mapper.MdEmployeeMapper;
import com.mesmax.mapper.MdMachineMapper;
import com.mesmax.mapper.ProdDispatchMapper;
import com.mesmax.mapper.ProdOrderMapper;
import com.mesmax.mapper.ProdOrderStepMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 派工单：生成、接单、批量派工、工票打印数据。 */
@RestController
@RequestMapping("/dispatch")
public class DispatchController extends BaseCrudController<ProdDispatch> {

    private final ProdDispatchMapper mapper;
    private final ProdOrderMapper orderMapper;
    private final ProdOrderStepMapper stepMapper;
    private final MdMachineMapper machineMapper;
    private final MdEmployeeMapper employeeMapper;
    private final ProductionService productionService;

    public DispatchController(ProdDispatchMapper mapper, ProdOrderMapper orderMapper, ProdOrderStepMapper stepMapper,
                              MdMachineMapper machineMapper, MdEmployeeMapper employeeMapper,
                              ProductionService productionService) {
        this.mapper = mapper;
        this.orderMapper = orderMapper;
        this.stepMapper = stepMapper;
        this.machineMapper = machineMapper;
        this.employeeMapper = employeeMapper;
        this.productionService = productionService;
    }

    @Override
    protected BaseMapper<ProdDispatch> mapper() {
        return mapper;
    }

    /** 创建派工单。 */
    @PostMapping("/create")
    public R<ProdDispatch> create(@RequestBody Map<String, Object> body) {
        Long stepId = Long.valueOf(String.valueOf(body.get("stepId")));
        Long machineId = blank(body.get("machineId")) ? null : Long.valueOf(String.valueOf(body.get("machineId")));
        Long employeeId = blank(body.get("employeeId")) ? null : Long.valueOf(String.valueOf(body.get("employeeId")));
        Long shiftId = blank(body.get("shiftId")) ? null : Long.valueOf(String.valueOf(body.get("shiftId")));
        BigDecimal qty = blank(body.get("planQty")) ? null : new BigDecimal(String.valueOf(body.get("planQty")));
        return R.ok("派工成功", productionService.createDispatch(stepId, machineId, employeeId, shiftId, qty));
    }

    /** 操作工接单。 */
    @PostMapping("/receive/{id}")
    public R<Void> receive(@PathVariable Long id) {
        productionService.receiveDispatch(id);
        return R.ok("已接单", null);
    }

    /** 批量派工：把待派工任务按操作工轮转分配。 */
    @PostMapping("/batch")
    public R<Map<String, Object>> batch(@RequestBody Map<String, Object> body) {
        Long wcId = blank(body.get("wcId")) ? null : Long.valueOf(String.valueOf(body.get("wcId")));
        Long shiftId = blank(body.get("shiftId")) ? null : Long.valueOf(String.valueOf(body.get("shiftId")));
        List<MdEmployee> operators = employeeMapper.selectList(new QueryWrapper<MdEmployee>()
                .like("post", "工").orderByAsc("id"));
        if (operators.isEmpty()) throw new BizException("没有可派工的操作工，请先维护员工主数据");

        QueryWrapper<ProdOrderStep> qw = new QueryWrapper<ProdOrderStep>()
                .in("status", List.of("PENDING", "DISPATCHED")).orderByAsc("plan_start").last("limit 100");
        if (wcId != null) qw.eq("wc_id", wcId);
        List<ProdOrderStep> steps = stepMapper.selectList(qw);

        int done = 0;
        for (int i = 0; i < steps.size(); i++) {
            try {
                productionService.createDispatch(steps.get(i).getId(), steps.get(i).getMachineId(),
                        operators.get(i % operators.size()).getId(), shiftId, steps.get(i).getPlanQty());
                done++;
            } catch (Exception ignored) {
                // 单个任务异常不影响批量结果
            }
        }
        return R.ok("批量派工完成", Map.of("total", steps.size(), "dispatched", done));
    }

    /** 工票打印数据：一张工票即可下车间（含程序号、刀具要求、操作工签名栏）。 */
    @GetMapping("/ticket/{id}")
    public R<Map<String, Object>> ticket(@PathVariable Long id) {
        ProdDispatch d = mapper.selectById(id);
        if (d == null) throw new BizException("派工单不存在");
        ProdOrder o = orderMapper.selectById(d.getOrderId());
        ProdOrderStep s = stepMapper.selectById(d.getStepId());
        MdMachine m = d.getMachineId() == null ? null : machineMapper.selectById(d.getMachineId());
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("dispatch", d);
        t.put("order", o);
        t.put("step", s);
        t.put("machine", m);
        t.put("printTime", LocalDateTime.now());
        t.put("qrContent", d.getDispatchNo() + "|" + d.getOrderNo() + "|" + (s == null ? "" : s.getProcCode()));
        return R.ok(t);
    }

    /** 按机床查询当前在制派工（机台悬挂看板 / 终端）。 */
    @GetMapping("/byMachine/{machineId}")
    public R<List<ProdDispatch>> byMachine(@PathVariable Long machineId) {
        return R.ok(mapper.selectList(new QueryWrapper<ProdDispatch>()
                .eq("machine_id", machineId)
                .in("status", List.of("DISPATCHED", "RECEIVED", "RUNNING"))
                .orderByAsc("plan_start")));
    }

    private static boolean blank(Object o) {
        return o == null || String.valueOf(o).isBlank() || "null".equals(String.valueOf(o));
    }
}
