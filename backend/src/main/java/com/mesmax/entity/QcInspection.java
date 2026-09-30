package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 检验单：首件(FIRST) / 巡检(PATROL) / 终检(FINAL) / 入库检(WAREHOUSE)。 */
@Data
@TableName("qc_inspection")
public class QcInspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String inspNo;
    private String inspType;
    private Long orderId;
    private String orderNo;
    private Long stepId;
    private String procName;
    private Long machineId;
    private String machineCode;
    private String snCode;
    private Long materialId;
    private String productName;
    private Integer sampleQty;
    private Integer qualifiedQty;
    private Integer defectQty;
    private String result;
    private String inspector;
    private LocalDateTime inspTime;
    private String conclusion;
    private String remark;
}
