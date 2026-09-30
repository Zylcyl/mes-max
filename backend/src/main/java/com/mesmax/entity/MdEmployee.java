package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("md_employee")
public class MdEmployee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String empNo;
    private String empName;
    private String deptName;
    private String post;
    private String skillLevel;
    private String teamName;
    private String phone;
    private LocalDate hireDate;
    private Integer status;
}
