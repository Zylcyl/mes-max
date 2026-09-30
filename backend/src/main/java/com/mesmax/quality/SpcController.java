package com.mesmax.quality;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.BizException;
import com.mesmax.common.R;
import com.mesmax.entity.QcSpcSample;
import com.mesmax.mapper.QcSpcSampleMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SPC 统计过程控制。
 *
 * <p>采用 <b>I-MR（单值-移动极差）控制图</b> 而不是 Xbar-R：
 * CNC 机加工的关键尺寸往往每件都要测（节拍慢、价值高），一个样本即一个观测值，
 * 无法凑齐 4~5 件的子组，I-MR 才是现场真正能落地的做法。
 *
 * <p>标准差 σ 用移动极差均值估计：σ = MR̄ / d2，其中 n=2 时 d2 = 1.128。
 */
@RestController
@RequestMapping("/spc")
public class SpcController extends BaseCrudController<QcSpcSample> {

    private static final double D2 = 1.128;
    private static final double D3 = 0.0;
    private static final double D4 = 3.267;

    private final QcSpcSampleMapper mapper;

    public SpcController(QcSpcSampleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<QcSpcSample> mapper() {
        return mapper;
    }

    @Override
    protected void beforeCreate(QcSpcSample entity) {
        if (entity.getSampleTime() == null) entity.setSampleTime(LocalDateTime.now());
    }

    /** 采集点列表，支持按机床 / 特性筛选。 */
    @GetMapping("/series")
    public R<Map<String, Object>> series(@RequestParam(required = false) Long machineId,
                                         @RequestParam(required = false) String charCode,
                                         @RequestParam(defaultValue = "50") int size) {
        QueryWrapper<QcSpcSample> qw = new QueryWrapper<>();
        if (machineId != null) qw.eq("machine_id", machineId);
        if (charCode != null && !charCode.isBlank()) qw.eq("char_code", charCode);
        // size 下限兜底：size=-1 会生成 `limit -1` 直接抛 SQL 语法错误
        qw.orderByDesc("sample_time").last("limit " + Math.max(5, Math.min(size, 500)));
        List<QcSpcSample> list = mapper.selectList(qw);
        list = new ArrayList<>(list);
        list.sort((a, b) -> {
            int c = a.getSampleTime().compareTo(b.getSampleTime());
            return c != 0 ? c : Integer.compare(a.getSampleNo() == null ? 0 : a.getSampleNo(),
                    b.getSampleNo() == null ? 0 : b.getSampleNo());
        });

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("samples", list);
        m.put("analysis", analyze(list));
        return R.ok(m);
    }

    /** 所有可选特性 / 机床组合。 */
    @GetMapping("/groups")
    public R<List<Map<String, Object>>> groups() {
        List<QcSpcSample> all = mapper.selectList(new QueryWrapper<QcSpcSample>()
                .orderByDesc("sample_time").last("limit 2000"));
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (QcSpcSample s : all) {
            String key = s.getMachineCode() + "|" + s.getCharCode();
            map.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("machineId", s.getMachineId());
                m.put("machineCode", s.getMachineCode());
                m.put("charCode", s.getCharCode());
                m.put("charName", s.getCharName());
                m.put("count", 0);
                return m;
            });
            map.get(key).put("count", (Integer) map.get(key).get("count") + 1);
        }
        return R.ok(new ArrayList<>(map.values()));
    }

    /** 计算过程能力指数与控制限。 */
    @PostMapping("/analyze")
    public R<Map<String, Object>> analyzeRequest(@RequestBody Map<String, Object> body) {
        Long machineId = body.get("machineId") == null ? null : Long.valueOf(String.valueOf(body.get("machineId")));
        String charCode = body.get("charCode") == null ? null : String.valueOf(body.get("charCode"));
        /*
         * 取「最近」200 个样本（与 /spc/series 的取数口径一致）。
         * 早先用 orderByAsc 取的是最旧的 200 条：样本一旦超过 200 个，
         * 新产生的超差样本就永远进不了控制图 —— 这是 SPC 最不能犯的错。
         */
        List<QcSpcSample> list = mapper.selectList(new QueryWrapper<QcSpcSample>()
                .eq(machineId != null, "machine_id", machineId)
                .eq(charCode != null && !charCode.isBlank(), "char_code", charCode)
                .orderByDesc("sample_time").last("limit 200"));
        list = new ArrayList<>(list);
        list.sort(Comparator.comparing(QcSpcSample::getSampleTime,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        if (list.size() < 5) throw new BizException("样本数量不足（至少 5 个），无法计算过程能力");
        return R.ok(analyze(list));
    }

    private Map<String, Object> analyze(List<QcSpcSample> list) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (list == null || list.size() < 5) {
            res.put("enough", false);
            return res;
        }
        double[] v = list.stream().mapToDouble(s -> s.getSampleValue().doubleValue()).toArray();
        double mean = 0;
        for (double x : v) mean += x;
        mean /= v.length;

        double[] mr = new double[v.length - 1];
        for (int i = 1; i < v.length; i++) mr[i - 1] = Math.abs(v[i] - v[i - 1]);
        double mrBar = 0;
        for (double x : mr) mrBar += x;
        mrBar /= mr.length;
        double sigma = mrBar / D2;

        QcSpcSample first = list.get(0);
        BigDecimal uslBd = first.getUpperLimit();
        BigDecimal lslBd = first.getLowerLimit();
        /*
         * 公差上下限缺失时就**不算**过程能力指数。
         *
         * 早先的写法是「缺规格限就用 mean ± 3σ 顶替」，于是 Cp 与 CpK 恒等于 1.0 ——
         * 一个看起来「过程能力刚好及格」的假数，比留空危险得多（与本项目在 OEE 上
         * 「拿不到定额就不编性能开动率」的原则也自相矛盾）。
         */
        boolean hasSpec = uslBd != null && lslBd != null && uslBd.doubleValue() > lslBd.doubleValue();
        double usl = hasSpec ? uslBd.doubleValue() : Double.NaN;
        double lsl = hasSpec ? lslBd.doubleValue() : Double.NaN;

        Double cp = null;
        Double cpk = null;
        Double cpu = null;
        Double cpl = null;
        if (hasSpec && sigma > 0) {
            cpu = (usl - mean) / (3 * sigma);
            cpl = (mean - lsl) / (3 * sigma);
            cp = (usl - lsl) / (6 * sigma);
            cpk = Math.min(cpu, cpl);
        }
        // 判定用的必须是「展示出来的那个数」：原来拿未取整的 cpk 比阈值，
        // 会出现「CpK 显示 1.000，结论却是 CpK<1.0 能力不足」的自相矛盾
        Double cpkShown = cpk == null ? null : round(cpk, 3);

        // 控制限来自移动极差（与规格限无关），始终可算
        double ucl = mean + 2.66 * mrBar;
        double lcl = mean - 2.66 * mrBar;
        double mrUcl = D4 * mrBar;
        double mrLcl = Math.max(0, D3 * mrBar);

        List<Map<String, Object>> points = new ArrayList<>();
        int outOfControl = 0;
        int outOfSpec = 0;
        for (QcSpcSample s : list) {
            double x = s.getSampleValue().doubleValue();
            boolean ooc = x > ucl || x < lcl;
            boolean oos = hasSpec && (x > usl || x < lsl);
            if (ooc) outOfControl++;
            if (oos) outOfSpec++;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("sampleNo", s.getSampleNo());
            p.put("time", s.getSampleTime());
            p.put("value", round(x, 4));
            p.put("machineCode", s.getMachineCode());
            p.put("outOfControl", ooc);
            p.put("outOfSpec", oos);
            points.add(p);
        }

        res.put("enough", true);
        res.put("sampleCount", list.size());
        res.put("charName", first.getCharName());
        res.put("machineCode", first.getMachineCode());
        res.put("mean", round(mean, 4));
        res.put("sigma", round(sigma, 5));
        res.put("mrBar", round(mrBar, 5));
        res.put("usl", hasSpec ? round(usl, 4) : null);
        res.put("lsl", hasSpec ? round(lsl, 4) : null);
        res.put("target", first.getNominalValue());
        res.put("ucl", round(ucl, 4));
        res.put("lcl", round(lcl, 4));
        res.put("mrUcl", round(mrUcl, 4));
        res.put("mrLcl", round(mrLcl, 4));
        res.put("cp", cp == null ? null : round(cp, 3));
        res.put("cpk", cpkShown);
        res.put("cpu", cpu == null ? null : round(cpu, 3));
        res.put("cpl", cpl == null ? null : round(cpl, 3));
        res.put("specSource", hasSpec ? "drawing" : "missing");
        res.put("outOfControl", outOfControl);
        res.put("outOfSpec", outOfSpec);
        res.put("grade", cpkShown == null
                ? "无法判定（未录入公差上下限）"
                : cpkShown >= 1.67 ? "优（CpK≥1.67，过程能力充足）"
                : cpkShown >= 1.33 ? "良（1.33≤CpK<1.67，过程能力尚可）"
                : cpkShown >= 1.0 ? "一般（1.0≤CpK<1.33，需改进）"
                : "不足（CpK<1.0，过程能力严重不足，须立即改善）");
        res.put("suggestion", cpkShown == null
                ? "该特性没有上下规格限，系统不计算 Cp/CpK。请先在图号/检验标准里录入公差，再复算过程能力。"
                : cpkShown >= 1.33
                    ? "维持当前切削参数与刀具寿命策略，按常规频次抽检即可。"
                    : "建议：核查刀补磨损补偿周期、缩短换刀间隔、复核装夹定位面与切削参数，并加密抽检频次。");
        res.put("points", points);
        return res;
    }

    /** 关键特性清单（供前端下拉）。 */
    @GetMapping("/characteristics")
    public R<List<String>> characteristics() {
        List<QcSpcSample> all = mapper.selectList(new QueryWrapper<QcSpcSample>().last("limit 500"));
        return R.ok(all.stream().map(QcSpcSample::getCharName).distinct().collect(Collectors.toList()));
    }

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
