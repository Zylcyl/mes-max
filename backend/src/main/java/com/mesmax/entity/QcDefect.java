package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不合格品处置单。处置四态闭环取自 WebErpMesv2 的 quality-derogations 设计：
 * 返工(REWORK) / 返修(REPAIR) / 让步接收(CONCESSION) / 报废(SCRAP)。
 */
@Data
@TableName("qc_defect")
public class QcDefect {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String defectNo;
    private Long orderId;
    private String orderNo;
    private Long stepId;
    private Long machineId;
    private String machineCode;
    private String snCode;
    private Long materialId;
    private String productName;
    private String defectType;
    private String defectDesc;
    private BigDecimal defectQty;
    private String reasonType;
    private String handleType;
    private String handleStatus;
    private String handler;
    private LocalDateTime handleTime;
    private BigDecimal costLoss;
    private String causeAnalysis;
    private String imageUrl;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String remark;
}
