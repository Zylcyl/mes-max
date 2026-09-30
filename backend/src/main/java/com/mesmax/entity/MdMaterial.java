package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("md_material")
public class MdMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String matCode;
    private String matName;
    private String spec;
    private String materialGrade;
    private String unit;
    /** 毛坯 / 半成品 / 成品 / 标准件 / 辅料 */
    private String matType;
    private String drawingNo;
    private BigDecimal safeStock;
    private BigDecimal currentStock;
    private BigDecimal unitCost;
    private BigDecimal standardPrice;
    private String processRoute;
    private Integer status;
    private String remark;
}
