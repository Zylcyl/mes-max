package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String ip;
    private String browser;
    private Integer status;
    private String msg;
    private LocalDateTime loginTime;
}
