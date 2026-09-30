package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报工记录。OEE 的时间开动率、性能开动率、合格率全部由本表与停机表推导。 */
@Data
@TableName("prod_task_report")
public class ProdTaskReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportNo;
    private Long dispatchId;
    private Long orderId;
    private String orderNo;
    private Long stepId;
    private Long machineId;
    private String machineCode;
    private Long employeeId;
    private String employeeName;
    private Long shiftId;
    private String shiftName;
    /** 开工 / 报工 / 完工 / 暂停 */
    private String reportType;
    private LocalDate workDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal runMinutes;
    private BigDecimal stopMinutes;
    private BigDecimal qty;
    private BigDecimal qualifiedQty;
    private BigDecimal defectQty;
    private String scrapReason;
    private String ncProgramNo;
    private String toolCode;
    private String toolOffsetNo;
    private LocalDateTime reportTime;
    private String remark;
}
