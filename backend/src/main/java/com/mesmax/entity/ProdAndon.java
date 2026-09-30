package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 安灯呼叫：质量异常 / 设备异常 / 物料短缺 / 工艺求助 / 刀具需求。 */
@Data
@TableName("prod_andon")
public class ProdAndon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String andonNo;
    private Long machineId;
    private String machineCode;
    private Long orderId;
    private String orderNo;
    private String andonType;
    /** 一级(班组) / 二级(车间) / 三级(厂部) */
    private String level;
    private String content;
    private String callUser;
    private LocalDateTime callTime;
    private String respondUser;
    private LocalDateTime respondTime;
    private LocalDateTime resolveTime;
    private BigDecimal durationMin;
    private String status;
    private String remark;
}
