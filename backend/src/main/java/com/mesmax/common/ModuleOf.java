package com.mesmax.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求路径 → 功能模块 的映射，用于角色权限校验与操作日志归类。
 *
 * <p>约定：读接口（GET / OPTIONS）默认对所有登录账号开放（车间看板的机台状态、订单进度、
 * 质量数据本来就是「谁都能看」的现场信息）；写接口（POST/PUT/DELETE）需要命中对应模块的权限标识。
 *
 * <p><b>例外</b>：账号、角色权限、登录/操作日志这类数据即使只读也不该对全厂开放 ——
 * 只读角色能拉到全部账号的手机号、以及别人写操作的参数，属于典型的越权读取。
 * 这些路径在 {@link #READ_PROTECTED} 里单独声明，读取同样需要对应模块权限。
 *
 * <p>注意：传入的路径必须是**已完成 URL 解码且去掉 context-path** 的查找路径
 * （由 {@code UrlPathHelper.getLookupPathForRequest} 得到）。若直接用
 * {@code request.getRequestURI()}，Tomcat 返回的是未解码路径，
 * {@code /api/%73ystem/...} 这类写法就会绕过模块判定。
 */
public final class ModuleOf {

    /** 前缀 → 权限标识。顺序无关，前缀之间不存在包含关系。 */
    private static final Map<String, String> PREFIX = new LinkedHashMap<>();

    /**
     * 读取也需要权限的路径前缀（其余读接口一律放行）。
     *
     * <p>注意字典读取（{@code /system/dict/**}）不在其中：前端启动时所有角色都要拉全量字典，
     * 它只包含下拉框用的枚举值，没有敏感信息。
     */
    private static final Map<String, String> READ_PROTECTED = new LinkedHashMap<>();

    static {
        PREFIX.put("/board", "dashboard");
        PREFIX.put("/machine", "dashboard");
        PREFIX.put("/order", "order");
        PREFIX.put("/step", "order");
        PREFIX.put("/schedule", "schedule");
        PREFIX.put("/dispatch", "dispatch");
        PREFIX.put("/report-task", "report");
        PREFIX.put("/stop", "report");
        PREFIX.put("/andon", "respond");
        PREFIX.put("/qc", "quality");
        PREFIX.put("/defect", "quality");
        PREFIX.put("/spc", "spc");
        PREFIX.put("/tool", "tool");
        PREFIX.put("/equipment", "equipment");
        PREFIX.put("/fault", "fault");
        PREFIX.put("/maint", "maint");
        PREFIX.put("/trace", "trace");
        PREFIX.put("/stats", "stats");
        PREFIX.put("/terminal", "terminal");
        PREFIX.put("/md", "masterdata");
        PREFIX.put("/system", "system");

        READ_PROTECTED.put("/system/user", "system");
        READ_PROTECTED.put("/system/role", "system");
        READ_PROTECTED.put("/system/log", "system");
    }

    private ModuleOf() {
    }

    /** 按前缀判定模块；未命中返回 null（公共接口：登录、个人资料、字典读取等）。 */
    public static String module(String lookupPath) {
        if (lookupPath == null || lookupPath.isEmpty()) return null;
        for (Map.Entry<String, String> e : PREFIX.entrySet()) {
            if (lookupPath.equals(e.getKey()) || lookupPath.startsWith(e.getKey() + "/")) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 该请求需要哪个模块权限。
     *
     * @return null 表示无需模块权限（读接口或非业务接口）
     */
    public static String requiredPermission(String method, String lookupPath) {
        if (method == null) return null;
        if ("OPTIONS".equalsIgnoreCase(method)) return null;
        if ("GET".equalsIgnoreCase(method)) return readProtected(lookupPath);
        return module(lookupPath);
    }

    /** 读接口是否需要模块权限；null 表示放行。 */
    private static String readProtected(String lookupPath) {
        if (lookupPath == null) return null;
        for (Map.Entry<String, String> e : READ_PROTECTED.entrySet()) {
            if (lookupPath.equals(e.getKey()) || lookupPath.startsWith(e.getKey() + "/")) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 接口路径 → 中文模块名，用于操作日志展示。 */
    public static String moduleName(String lookupPath) {
        String module = module(lookupPath);
        if (module == null) return "其它";
        return switch (module) {
            case "dashboard" -> "设备与看板";
            case "order" -> "生产订单";
            case "schedule" -> "生产排产";
            case "dispatch" -> "派工管理";
            case "report" -> "车间报工";
            case "respond" -> "安灯呼叫";
            case "quality" -> "质量管理";
            case "spc" -> "SPC 分析";
            case "tool" -> "刀具管理";
            case "equipment" -> "设备管理";
            case "fault" -> "故障管理";
            case "maint" -> "保养管理";
            case "trace" -> "谱系追溯";
            case "stats" -> "统计报表";
            case "terminal" -> "车间终端";
            case "masterdata" -> "基础主数据";
            case "system" -> "系统管理";
            default -> "其它";
        };
    }
}
