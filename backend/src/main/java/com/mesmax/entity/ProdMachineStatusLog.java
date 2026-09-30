package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 机床状态机日志：每次状态跃迁落一条，是设备利用率与状态甘特的原始数据。 */
@Data
@TableName("prod_machine_status_log")
public class ProdMachineStatusLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long machineId;
    private String machineCode;
    private String status;
    private String prevStatus;
    private String reason;
    private String orderNo;
    private String ncProgramNo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSec;
    private String operator;
}
