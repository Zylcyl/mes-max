package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派工单 —— 车间现场的最小执行单元，对应「一张纸质工票 + 一台机床 + 一个人 + 一个班次」。
 * 机加工车间至今仍需要打印工票随工件流转，因此该实体保留完整的打印所需字段。
 */
@Data
@TableName("prod_dispatch")
public class ProdDispatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dispatchNo;
    private Long orderId;
    private String orderNo;
    private Long stepId;
    private Integer seqNo;
    private String procName;
    private Long machineId;
    private String machineCode;
    private Long employeeId;
    private String employeeName;
    private String teamName;
    private Long shiftId;
    private String shiftName;
    private BigDecimal planQty;
    private LocalDateTime planStart;
    private LocalDateTime planEnd;
    private LocalDateTime dispatchTime;
    private LocalDateTime receiveTime;
    private String status;
    private String ncProgramNo;
    private String remark;
}
