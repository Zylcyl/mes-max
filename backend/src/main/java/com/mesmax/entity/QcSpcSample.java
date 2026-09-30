package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SPC 关键尺寸采样点：用于绘制 Xbar-R 控制图并计算 Cp/Cpk。 */
@Data
@TableName("qc_spc_sample")
public class QcSpcSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String charName;
    private String charCode;
    private Long machineId;
    private String machineCode;
    private Long orderId;
    private String orderNo;
    private String snCode;
    private BigDecimal nominalValue;
    private BigDecimal upperLimit;
    private BigDecimal lowerLimit;
    private BigDecimal sampleValue;
    private Integer sampleNo;
    private LocalDateTime sampleTime;
    private String inspector;
    private String remark;
}
