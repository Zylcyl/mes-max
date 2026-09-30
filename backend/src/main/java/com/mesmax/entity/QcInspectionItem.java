package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("qc_inspection_item")
public class QcInspectionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inspectionId;
    private String itemName;
    private String itemType;
    private BigDecimal nominalValue;
    private BigDecimal upperTol;
    private BigDecimal lowerTol;
    private String unit;
    private BigDecimal measureValue;
    private String result;
    private String gaugeUsed;
    private String remark;
}
