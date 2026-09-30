package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 刀具使用记录：装刀-加工-卸刀全过程，支撑「一把刀加工过哪些工件」的反向追溯。 */
@Data
@TableName("tool_usage")
public class ToolUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long toolId;
    private String toolCode;
    private String toolName;
    private Long machineId;
    private String machineCode;
    private Long orderId;
    private String orderNo;
    private Long stepId;
    private String ncProgramNo;
    private String offsetNo;
    private LocalDateTime mountTime;
    private LocalDateTime unmountTime;
    private BigDecimal useMinutes;
    private Integer partQty;
    private BigDecimal wearValue;
    private String unmountReason;
    private String operator;
}
