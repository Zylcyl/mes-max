package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("eq_maint_plan")
public class EqMaintPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String planCode;
    private String planName;
    private Long machineId;
    private String machineCode;
    /** 定期保养 / 精度校验 / 耗材更换 */
    private String maintType;
    /** 月 / 季 / 年 */
    private String periodType;
    private Integer periodValue;
    private LocalDate lastDate;
    private LocalDate nextDate;
    private String principal;
    private String content;
    private String status;
}
