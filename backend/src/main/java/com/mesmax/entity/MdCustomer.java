package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("md_customer")
public class MdCustomer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String custCode;
    private String custName;
    private String contact;
    private String phone;
    private String custLevel;
    private String industry;
    private String address;
    private Integer status;
    private String remark;
}
