package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("md_work_center")
public class MdWorkCenter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String wcCode;
    private String wcName;
    private Long workshopId;
    private String wcType;
    private BigDecimal capacityHour;
    private Integer status;
    private String remark;
}
