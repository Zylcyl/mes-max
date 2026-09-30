package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 工单工序任务。由生产订单按工艺路线展开生成，定额工时在此快照，保证后续改工艺不影响在制工单。 */
@Data
@TableName("prod_order_step")
public class ProdOrderStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;
    private Integer seqNo;
    private Long procId;
    private String procCode;
    private String procName;
    private Long wcId;
    private String wcName;
    private Long machineId;
    private String machineCode;
    private LocalDateTime planStart;
    private LocalDateTime planEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
    private BigDecimal planQty;
    private BigDecimal finishedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal defectQty;
    private BigDecimal setupTime;
    private BigDecimal pieceTime;
    private BigDecimal runMinutes;
    private BigDecimal stopMinutes;
    private String ncProgramNo;
    private String ncProgramVer;
    private String toolRequirement;
    private Integer isKeyProc;
    private Integer isInspection;
    private String status;
    private String remark;
}
