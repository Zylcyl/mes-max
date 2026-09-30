package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 刃磨记录：刃磨后重置刀具寿命计数器，并扣减可刃磨余量。 */
@Data
@TableName("tool_grind")
public class ToolGrind {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long toolId;
    private String toolCode;
    private String grindNo;
    /** 刃磨 / 重涂 / 报废 */
    private String grindType;
    private String beforeSpec;
    private String afterSpec;
    private BigDecimal grindAmount;
    private LocalDateTime grindTime;
    private String grinder;
    private String result;
    private BigDecimal cost;
    private String remark;
}
