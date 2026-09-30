package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("md_workshop")
public class MdWorkshop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String wsCode;
    private String wsName;
    private String manager;
    private String phone;
    private BigDecimal areaSqm;
    private Integer status;
    private String remark;
}
