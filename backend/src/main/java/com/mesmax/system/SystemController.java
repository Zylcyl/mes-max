package com.mesmax.system;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mesmax.common.BizException;
import com.mesmax.common.PermRegistry;
import com.mesmax.common.Passwords;
import com.mesmax.common.R;
import com.mesmax.entity.SysDict;
import com.mesmax.entity.SysLoginLog;
import com.mesmax.entity.SysOperLog;
import com.mesmax.entity.SysRole;
import com.mesmax.entity.SysUser;
import com.mesmax.mapper.SysDictMapper;
import com.mesmax.mapper.SysLoginLogMapper;
import com.mesmax.mapper.SysOperLogMapper;
import com.mesmax.mapper.SysRoleMapper;
import com.mesmax.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 系统管理：用户、角色、字典、登录与操作日志。 */
@RestController
@RequestMapping("/system")
public class SystemController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDictMapper dictMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;

    /** 新建账号 / 重置口令时使用的初始口令（仅演示环境用，不参与登录校验）。 */
    @Value("${mes-max.security.default-password:mes123456}")
    private String defaultPassword;

    public SystemController(SysUserMapper userMapper, SysRoleMapper roleMapper, SysDictMapper dictMapper,
                            SysLoginLogMapper loginLogMapper, SysOperLogMapper operLogMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.dictMapper = dictMapper;
        this.loginLogMapper = loginLogMapper;
        this.operLogMapper = operLogMapper;
    }

    // ---------------- 用户 ----------------

    @GetMapping("/user/list")
    public R<List<Map<String, Object>>> users(@RequestParam(required = false) String keyword) {
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("username", keyword).or().like("real_name", keyword).or().like("emp_no", keyword));
        }
        qw.orderByAsc("id");
        List<Map<String, Object>> list = userMapper.selectList(qw).stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("empNo", u.getEmpNo());
            m.put("roleCode", u.getRoleCode());
            m.put("deptName", u.getDeptName());
            m.put("phone", u.getPhone());
            m.put("status", u.getStatus());
            m.put("lastLoginTime", u.getLastLoginTime());
            m.put("createTime", u.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        return R.ok(list);
    }

    @PostMapping("/user/save")
    public R<SysUser> saveUser(@RequestBody SysUser user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new BizException("账号不能为空");
        }
        user.setUsername(user.getUsername().trim());
        if (user.getId() == null) {
            /*
             * 账号必须唯一：sys_user 上并没有唯一索引（历史库可能存在重复行），
             * 而登录查的是 selectOne(... limit 1) —— 一旦插进两个同名账号，
             * 「改了口令却登不进去」这类问题会极难排查。
             */
            Long dup = userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", user.getUsername()));
            if (dup != null && dup > 0) {
                throw new BizException(BizException.CONFLICT, "账号已存在：" + user.getUsername());
            }
            // 新建账号：未指定口令则用演示默认口令，但一律以 BCrypt 哈希入库
            String raw = (user.getPassword() == null || user.getPassword().isBlank()) ? defaultPassword : user.getPassword();
            user.setPassword(Passwords.hash(raw));
            userMapper.insert(user);
        } else {
            // 更新账号：前端不回吐口令，因此这里收到的 password 通常是空的 —— 置 null 表示不改口令；
            // 若显式传了新口令，则哈希后更新
            if (user.getPassword() != null && !user.getPassword().isBlank()) {
                user.setPassword(Passwords.hash(user.getPassword()));
            } else {
                user.setPassword(null);
            }
            userMapper.updateById(user);
        }
        return R.ok("保存成功", user);
    }

    @DeleteMapping("/user/delete/{id}")
    public R<Void> deleteUser(@PathVariable Long id) {
        userMapper.deleteById(id);
        return R.ok("删除成功", null);
    }

    @PostMapping("/user/resetPassword/{id}")
    public R<Void> resetPassword(@PathVariable Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(BizException.NOT_FOUND, "用户不存在：" + id);
        u.setPassword(Passwords.hash(defaultPassword));
        userMapper.updateById(u);
        return R.ok("密码已重置为初始口令", null);
    }

    // ---------------- 角色 ----------------

    @GetMapping("/role/list")
    public R<List<SysRole>> roles() {
        return R.ok(roleMapper.selectList(new QueryWrapper<SysRole>().orderByAsc("id")));
    }

    @PostMapping("/role/save")
    public R<SysRole> saveRole(@RequestBody SysRole role) {
        if (role.getId() == null) roleMapper.insert(role);
        else roleMapper.updateById(role);
        // 权限改动立即生效：刷新内存中的角色权限注册表，避免必须重启才生效
        PermRegistry.put(role.getRoleCode(), role.getPerms());
        return R.ok("保存成功", role);
    }

    /** 重新装载全部角色权限（批量改完权限后一键刷新）。 */
    @PostMapping("/role/reload")
    public R<Integer> reloadRoles() {
        int n = 0;
        for (SysRole r : roleMapper.selectList(null)) {
            PermRegistry.put(r.getRoleCode(), r.getPerms());
            n++;
        }
        return R.ok("已重新装载 " + n + " 个角色的权限", n);
    }

    // ---------------- 字典 ----------------

    /** 一次性返回全部字典，前端启动时缓存（数据量极小）。 */
    @GetMapping("/dict/all")
    public R<Map<String, List<SysDict>>> dictAll() {
        // 过滤掉 dict_type 为空的历史脏数据：分组时 null 键会直接抛
        // "element cannot be mapped to a null key"，把前端启动卡死。
        Map<String, List<SysDict>> map = dictMapper.selectList(new QueryWrapper<SysDict>()
                        .isNotNull("dict_type").ne("dict_type", "")
                        .orderByAsc("dict_type").orderByAsc("sort_no"))
                .stream().collect(Collectors.groupingBy(SysDict::getDictType,
                        LinkedHashMap::new, Collectors.toList()));
        return R.ok(map);
    }

    @GetMapping("/dict/list")
    public R<List<SysDict>> dictList(@RequestParam(required = false) String dictType) {
        QueryWrapper<SysDict> qw = new QueryWrapper<>();
        if (dictType != null && !dictType.isBlank()) qw.eq("dict_type", dictType);
        qw.orderByAsc("dict_type").orderByAsc("sort_no");
        return R.ok(dictMapper.selectList(qw));
    }

    @PostMapping("/dict/save")
    public R<SysDict> saveDict(@RequestBody SysDict dict) {
        if (isBlank(dict.getDictType()) || isBlank(dict.getDictLabel()) || isBlank(dict.getDictValue())) {
            throw new BizException("字典类型、显示名称、字典值都不能为空");
        }
        if (dict.getId() == null) dictMapper.insert(dict);
        else dictMapper.updateById(dict);
        return R.ok("保存成功", dict);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @DeleteMapping("/dict/delete/{id}")
    public R<Void> deleteDict(@PathVariable Long id) {
        dictMapper.deleteById(id);
        return R.ok("删除成功", null);
    }

    // ---------------- 日志 ----------------

    @GetMapping("/log/login")
    public R<List<SysLoginLog>> loginLogs(@RequestParam(defaultValue = "100") int size) {
        return R.ok(loginLogMapper.selectList(new QueryWrapper<SysLoginLog>()
                .orderByDesc("login_time").last("limit " + Math.min(size, 500))));
    }

    @GetMapping("/log/oper")
    public R<List<SysOperLog>> operLogs(@RequestParam(defaultValue = "100") int size) {
        return R.ok(operLogMapper.selectList(new QueryWrapper<SysOperLog>()
                .orderByDesc("oper_time").last("limit " + Math.min(size, 500))));
    }
}
