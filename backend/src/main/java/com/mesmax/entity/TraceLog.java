package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 过程追溯记录（正向/反向追溯的骨架）。
 * 一条记录 = 「某序列号的某道工序，在某台机床、由某人、用某把刀、按某个程序、加工了多久」。
 */
@Data
@TableName("trace_log")
public class TraceLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snCode;
    private Long orderId;
    private String orderNo;
    private Long stepId;
    private Integer seqNo;
    private String procName;
    private Long machineId;
    private String machineCode;
    private Long employeeId;
    private String operator;
    private String ncProgramNo;
    private String toolCode;
    private String toolOffsetNo;
    private String materialBatch;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal qty;
    private BigDecimal runMinutes;
    private String qcResult;
    private String paramJson;
    private String shiftName;
    private String remark;
}
