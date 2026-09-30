package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("md_bom_item")
public class MdBomItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bomId;
    private Long matId;
    private String matCode;
    private String matName;
    private BigDecimal qtyPer;
    private BigDecimal scrapRate;
    private String itemType;
    private String remark;
}
