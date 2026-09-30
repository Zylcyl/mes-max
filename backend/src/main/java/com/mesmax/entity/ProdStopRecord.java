package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停机记录。CNC 车间的停机必须按原因归类（换刀/换型/故障/待料/待检/保养），
 * 否则 OEE 的时间损失无法改进 —— 这一设计取自 industry4.0-mes 的 stoppage +
 * deviation-causes-reporting 插件。
 */
@Data
@TableName("prod_stop_record")
public class ProdStopRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long machineId;
    private String machineCode;
    private Long orderId;
    private String orderNo;
    private Long stepId;
    /** 非计划停机 / 计划停机 */
    private String stopType;
    private String reasonCode;
    private String reasonDesc;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal durationMin;
    private String handler;
    private BigDecimal affectQty;
    private String shiftName;
    private String status;
    private String remark;
}
