package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 生产订单（车间工单）。priorityScore 为 JobShop 加权优先级评分，越大越先排产。 */
@Data
@TableName("prod_order")
public class ProdOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private Long productMatId;
    private String productCode;
    private String productName;
    private String productSpec;
    private BigDecimal planQty;
    private BigDecimal finishedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal defectQty;
    private LocalDateTime planStart;
    private LocalDateTime planEnd;
    /** 加急 / 高 / 中 / 低 */
    private String priority;
    private Integer priorityScore;
    private Long routingId;
    /** 量产 / 试制 / 返修 / 补件 */
    private String orderType;
    private String status;
    private String source;
    private String salesNo;
    private String ncProgramNo;
    private Integer isLocked;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private String remark;
}
