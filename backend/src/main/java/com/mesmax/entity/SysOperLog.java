package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String action;
    private String uri;
    private String method;
    private String params;
    private String username;
    private String ip;
    private Long costMs;
    private Integer status;
    private String msg;
    private LocalDateTime operTime;
}
