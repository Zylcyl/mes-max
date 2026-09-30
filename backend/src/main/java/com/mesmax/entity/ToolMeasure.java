package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对刀测量记录：刀补号、刀尖圆弧半径、刀具长度与偏差。 */
@Data
@TableName("tool_measure")
public class ToolMeasure {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long toolId;
    private String toolCode;
    private Long machineId;
    private String machineCode;
    private String offsetNo;
    private BigDecimal radiusValue;
    private BigDecimal lengthValue;
    private BigDecimal deviation;
    private LocalDateTime measureTime;
    private String measurer;
    private String result;
    private String remark;
}
