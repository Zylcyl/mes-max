package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /**
     * 口令只以 BCrypt 哈希存储，且永不序列化返回给前端。
     *
     * <p>这里必须用 {@code WRITE_ONLY} 而不是 {@code @JsonIgnore}：
     * {@code @JsonIgnore} 会**连反序列化一起屏蔽** —— 新建用户时请求体里的 password
     * 会被直接丢弃，于是「新建账号后用自己的口令登不进去」。
     * {@code WRITE_ONLY} 允许请求写入、禁止响应输出，才是这里想要的语义。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String realName;
    private String empNo;
    private String roleCode;
    private String deptName;
    private String phone;
    private Integer status;
    private LocalDateTime lastLoginTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
