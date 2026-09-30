package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/** 设备点检项（开机点检表条目）。 */
@Data
@TableName("eq_check_item")
public class EqCheckItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String itemCode;
    private String itemName;
    private String macType;
    private String checkPart;
    private String checkMethod;
    private String standard;
    /** 每班 / 每周 / 每月 */
    private String periodType;
    private Integer isKey;
    private Integer sortNo;
    private Integer status;
}
