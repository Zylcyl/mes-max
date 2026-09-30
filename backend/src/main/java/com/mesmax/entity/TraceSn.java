package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 产品序列号主档（追溯入口）。 */
@Data
@TableName("trace_sn")
public class TraceSn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snCode;
    private Long orderId;
    private String orderNo;
    private Long materialId;
    private String productCode;
    private String productName;
    private String batchNo;
    private Integer currentStep;
    private String currentProc;
    private String machineCode;
    /** 在制 / 完工 / 报废 / 待检 */
    private String status;
    private String qcResult;
    private LocalDateTime createTime;
    private LocalDateTime completeTime;
    private String remark;
}
