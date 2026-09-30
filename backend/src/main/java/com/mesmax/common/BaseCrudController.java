package com.mesmax.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.core.ResolvableType;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用增删改查基类 —— 主数据类实体（车间/机床/物料/工序/刀具…）无需重复写 CRUD。
 *
 * <p>分页查询支持三种过滤：
 * <ol>
 *   <li>字段等值：{@code ?macCode=MC-001&status=RUN}</li>
 *   <li>区间：{@code ?planStartStart=2026-09-01 00:00:00&planStartEnd=2026-09-02 00:00:00}</li>
 *   <li>模糊：{@code ?keyword=VMC} 等价于所有字符串字段 OR LIKE</li>
 * </ol>
 * 排序：{@code ?sort=createTime&order=desc}
 */
public abstract class BaseCrudController<T> {

    private static final Set<String> RESERVED = Set.of(
            "page", "size", "sort", "order", "keyword", "asc", "desc");

    /** 单页最大条数：防止 ?size=2000000000 一次拉全表把服务打满（10 个主数据控制器都继承本类）。 */
    private static final long MAX_PAGE_SIZE = 500;
    private static final long DEFAULT_PAGE_SIZE = 20;

    private static final Map<Class<?>, List<String>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<String>> STRING_FIELD_CACHE = new ConcurrentHashMap<>();

    protected abstract BaseMapper<T> mapper();

    @SuppressWarnings("unchecked")
    protected Class<T> entityClass() {
        // 用 ResolvableType 而不是已废弃的 GenericTypeResolver.resolveTypeArgument
        return (Class<T>) ResolvableType.forClass(getClass()).as(BaseCrudController.class).getGeneric(0).resolve();
    }

    @GetMapping("/page")
    public R<PageResult<T>> page(@RequestParam Map<String, String> params) {
        // 页码与页大小都要兜底：page=-1 会生成 OFFSET -20 直接抛 SQL 异常，
        // size 不设上限则等于开了个全表导出的口子。
        long page = Math.max(1, parseLong(params.get("page"), 1));
        long size = Math.min(MAX_PAGE_SIZE, Math.max(1, parseLong(params.get("size"), DEFAULT_PAGE_SIZE)));
        QueryWrapper<T> qw = buildWrapper(params);
        IPage<T> p = mapper().selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    @GetMapping("/list")
    public R<List<T>> list(@RequestParam Map<String, String> params) {
        // list 接口同样限流：取前 MAX_PAGE_SIZE 条，避免被当成全量导出使用
        return R.ok(mapper().selectList(buildWrapper(params).last("limit " + MAX_PAGE_SIZE)));
    }

    @GetMapping("/{id}")
    public R<T> get(@PathVariable Long id) {
        T t = mapper().selectById(id);
        if (t == null) throw new BizException(BizException.NOT_FOUND, "记录不存在：" + id);
        return R.ok(t);
    }

    /**
     * 保存。默认「带 id 视为更新，不带 id 视为新增」。
     *
     * <p>子类不覆写本方法，而是通过 {@link #beforeCreate(Object)} / {@link #beforeUpdate(Object)}
     * 钩子注入业务规则 —— 因为泛型参数 {@code T} 擦除后方法签名是 {@code save(Object)}，
     * 子类写 {@code save(ProdOrder)} 只是重载而非覆写，会导致同一路径注册两个处理器而启动失败。
     *
     * <p>新增/更新走两个不同钩子很重要：带 id 的请求体往往是「只改了某几个字段」的部分对象，
     * 若在更新时也按新增逻辑补默认值，会把派生字段（如刀具寿命占用率）依据缺失的输入重算成 0。
     */
    @PostMapping("/save")
    public R<T> save(@RequestBody T entity) {
        Long id = idOf(entity);
        boolean insert = !(upsert() && id != null);
        if (insert) {
            // 拒绝「全字段为空」的新增：空 body（{}）会在任何一张主数据表里插出一条
            // 只有 id 的垃圾记录 —— 例如一条 dict_type 为 null 的字典行，
            // 会让按类型分组的 /system/dict/all 直接 500，把整个前端启动卡住。
            if (isAllNull(entity)) throw new BizException("请求体为空，已拒绝新增记录");
            beforeCreate(entity);
            mapper().insert(entity);
        } else {
            beforeUpdate(entity);
            mapper().updateById(entity);
        }
        afterSave(entity);
        return R.ok(saveMessage(), entity);
    }

    @PutMapping("/update")
    public R<T> update(@RequestBody T entity) {
        // 没有主键的「更新」在 updateById 里会退化成「不更新任何行」，静默返回成功最难排查
        if (idOf(entity) == null) {
            throw new BizException("更新请求缺少主键 id");
        }
        // 与 /save 带 id 的分支保持一致：走同一个更新钩子，
        // 否则从 PUT 进来的局部更新会绕过 beforeUpdate（例如把刀具寿命占用率写坏）
        beforeUpdate(entity);
        mapper().updateById(entity);
        return R.ok("更新成功", entity);
    }

    @DeleteMapping("/delete/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mapper().deleteById(id);
        return R.ok("删除成功", null);
    }

    @PostMapping("/delete/batch")
    public R<Void> deleteBatch(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) mapper().deleteBatchIds(ids);
        return R.ok("批量删除成功", null);
    }

    // ------------------------------------------------------------------
    //  子类扩展点
    // ------------------------------------------------------------------

    /** 新增前填充业务字段（单号、默认值、外键带出等）。 */
    protected void beforeCreate(T entity) {
    }

    /** 更新前处理派生字段。注意：请求体可能是部分字段，缺失值不要当成 0 处理。 */
    protected void beforeUpdate(T entity) {
    }

    /** 保存后副作用（写日志、联动其它表）。 */
    protected void afterSave(T entity) {
    }

    /** 是否允许「带 id 即更新」。需要严格区分新增/更新的子类可返回 false。 */
    protected boolean upsert() {
        return true;
    }

    protected String saveMessage() {
        return "保存成功";
    }

    private static Long idOf(Object entity) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            Object v = f.get(entity);
            return v instanceof Number n ? n.longValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLong(String v, long defaultValue) {
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 除主键外所有字段都为 null，视为空请求体。 */
    private static boolean isAllNull(Object entity) {
        for (Field f : entity.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || "id".equals(f.getName())) continue;
            try {
                f.setAccessible(true);
                if (f.get(entity) != null) return false;
            } catch (Exception e) {
                return false; // 取不到值就按「非空」处理，宁可放过也不要误拦
            }
        }
        return true;
    }

    protected QueryWrapper<T> buildWrapper(Map<String, String> params) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        List<String> fields = fields(entityClass());
        for (Map.Entry<String, String> e : params.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (RESERVED.contains(key) || val == null || val.isBlank()) continue;

            if (fields.contains(key)) {
                qw.eq(StringUtils.camelToUnderline(key), val);
            } else if (key.endsWith("Start") && fields.contains(decap(key, 5))) {
                qw.ge(StringUtils.camelToUnderline(decap(key, 5)), val);
            } else if (key.endsWith("End") && fields.contains(decap(key, 3))) {
                qw.le(StringUtils.camelToUnderline(decap(key, 3)), val);
            }
        }
        appendKeyword(qw, params.get("keyword"));
        appendSort(qw, params.get("sort"), params.getOrDefault("order", "desc"));
        return qw;
    }

    protected void appendKeyword(QueryWrapper<T> qw, String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        List<String> strFields = stringFields(entityClass());
        if (strFields.isEmpty()) return;
        qw.and(w -> {
            for (int i = 0; i < strFields.size(); i++) {
                if (i == 0) w.like(StringUtils.camelToUnderline(strFields.get(i)), keyword);
                else w.or().like(StringUtils.camelToUnderline(strFields.get(i)), keyword);
            }
        });
    }

    protected void appendSort(QueryWrapper<T> qw, String sort, String order) {
        if (sort == null || sort.isBlank()) return;
        for (String s : sort.split(",")) {
            String field = s.trim();
            if (fields(entityClass()).contains(field)) {
                qw.orderBy(true, !"desc".equalsIgnoreCase(order), StringUtils.camelToUnderline(field));
            }
        }
    }

    private static String decap(String key, int suffixLen) {
        String prefix = key.substring(0, key.length() - suffixLen);
        return Character.toLowerCase(prefix.charAt(0)) + prefix.substring(1);
    }

    private static List<String> fields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, c -> {
            List<String> list = new ArrayList<>();
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) list.add(f.getName());
            }
            return list;
        });
    }

    private static List<String> stringFields(Class<?> clazz) {
        return STRING_FIELD_CACHE.computeIfAbsent(clazz, c -> {
            List<String> list = new ArrayList<>();
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) list.add(f.getName());
            }
            return list;
        });
    }
}
