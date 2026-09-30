package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 刀具台账。lifeRatio = 已用寿命 / 标准寿命，超过 85% 进入预警、超过 100% 强制刃磨。 */
@Data
@TableName("tool_ledger")
public class ToolLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String toolCode;
    private String toolName;
    private String toolType;
    private String spec;
    private String materialGrade;
    private String coating;
    private BigDecimal stdLifeMin;
    private BigDecimal usedLifeMin;
    private Integer stdLifeQty;
    private Integer usedQty;
    private BigDecimal lifeRatio;
    private String location;
    private Long machineId;
    private String machineCode;
    private String offsetNo;
    /** 在用 / 在库 / 预警 / 待刃磨 / 已报废 */
    private String status;
    private String supplier;
    private BigDecimal unitPrice;
    private LocalDate purchaseDate;
    private String remark;
}
