package com.mesmax.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.BizException;
import com.mesmax.common.R;
import com.mesmax.common.UserContext;
import com.mesmax.entity.ProdAndon;
import com.mesmax.mapper.ProdAndonMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安灯（Andon）系统。
 *
 * <p>安灯不是「消息通知」，而是车间墙上的呼叫看板：一线呼叫 → 班组响应 → 车间/厂部升级 → 关闭。
 * 响应时长与关闭时长是衡量车间支持效率的核心指标，因此每次流转都留痕。
 */
@RestController
@RequestMapping("/andon")
public class AndonController extends BaseCrudController<ProdAndon> {

    private final ProdAndonMapper mapper;

    public AndonController(ProdAndonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<ProdAndon> mapper() {
        return mapper;
    }

    /** 未关闭的呼叫（看板用）。 */
    @GetMapping("/open")
    public R<List<ProdAndon>> open() {
        return R.ok(mapper.selectList(new QueryWrapper<ProdAndon>()
                .in("status", List.of("待响应", "处理中"))
                .orderByAsc("level").orderByAsc("call_time")));
    }

    /** 响应呼叫。 */
    @PostMapping("/respond/{id}")
    public R<ProdAndon> respond(@PathVariable Long id) {
        ProdAndon a = require(id);
        a.setRespondUser(UserContext.realName());
        a.setRespondTime(LocalDateTime.now());
        a.setStatus("处理中");
        mapper.updateById(a);
        return R.ok("已响应", a);
    }

    /** 关闭呼叫。 */
    @PostMapping("/resolve/{id}")
    public R<ProdAndon> resolve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProdAndon a = require(id);
        LocalDateTime now = LocalDateTime.now();
        if (a.getRespondTime() == null) {
            a.setRespondTime(now);
            a.setRespondUser(UserContext.realName());
        }
        a.setResolveTime(now);
        a.setDurationMin(BigDecimal.valueOf(Duration.between(a.getCallTime(), now).toMinutes()));
        a.setStatus("已关闭");
        if (body != null && body.get("remark") != null) a.setRemark(String.valueOf(body.get("remark")));
        mapper.updateById(a);
        return R.ok("呼叫已关闭", a);
    }

    /** 升级呼叫等级（一级班组 → 二级车间 → 三级厂部）。 */
    @PostMapping("/escalate/{id}")
    public R<ProdAndon> escalate(@PathVariable Long id) {
        ProdAndon a = require(id);
        a.setLevel(switch (a.getLevel() == null ? "一级" : a.getLevel()) {
            case "一级" -> "二级";
            case "二级" -> "三级";
            default -> "三级";
        });
        mapper.updateById(a);
        return R.ok("已升级至 " + a.getLevel(), a);
    }

    /** 安灯统计。 */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(defaultValue = "7") int days) {
        int span = Math.max(1, Math.min(days, 365));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = LocalDate.now().minusDays(span - 1L).atStartOfDay();
        List<ProdAndon> list = mapper.selectList(new QueryWrapper<ProdAndon>()
                .ge("call_time", from).le("call_time", now));

        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        Map<String, Integer> byMachine = new LinkedHashMap<>();
        double respondSum = 0;
        int respondCount = 0;
        double closeSum = 0;
        int closeCount = 0;

        for (ProdAndon a : list) {
            byType.merge(a.getAndonType() == null ? "其他" : a.getAndonType(), 1, Integer::sum);
            byLevel.merge(a.getLevel() == null ? "一级" : a.getLevel(), 1, Integer::sum);
            byMachine.merge(a.getMachineCode() == null ? "-" : a.getMachineCode(), 1, Integer::sum);
            if (a.getCallTime() != null && a.getRespondTime() != null) {
                respondSum += Duration.between(a.getCallTime(), a.getRespondTime()).toMinutes();
                respondCount++;
            }
            if (a.getCallTime() != null && a.getResolveTime() != null) {
                closeSum += Duration.between(a.getCallTime(), a.getResolveTime()).toMinutes();
                closeCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", span);
        result.put("total", list.size());
        result.put("openCount", list.stream().filter(a -> !"已关闭".equals(a.getStatus())).count());
        result.put("avgRespondMinutes", respondCount == 0 ? 0 : Math.round(respondSum / respondCount * 10) / 10.0);
        result.put("avgCloseMinutes", closeCount == 0 ? 0 : Math.round(closeSum / closeCount * 10) / 10.0);
        result.put("byType", byType);
        result.put("byLevel", byLevel);
        result.put("byMachine", byMachine);
        result.put("open", mapper.selectList(new QueryWrapper<ProdAndon>()
                .in("status", List.of("待响应", "处理中")).orderByAsc("call_time")));
        return R.ok(result);
    }

    private ProdAndon require(Long id) {
        ProdAndon a = mapper.selectById(id);
        if (a == null) throw new BizException("安灯记录不存在");
        return a;
    }
}
