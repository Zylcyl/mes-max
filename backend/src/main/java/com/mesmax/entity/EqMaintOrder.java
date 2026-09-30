package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("eq_maint_order")
public class EqMaintOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String maintNo;
    private Long planId;
    private Long machineId;
    private String machineCode;
    private String maintType;
    private String faultDesc;
    private String maintainer;
    private LocalDateTime planStart;
    private LocalDateTime planEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
    private BigDecimal downMinutes;
    private String spareParts;
    private BigDecimal cost;
    private String result;
    private String status;
    private String remark;
}
