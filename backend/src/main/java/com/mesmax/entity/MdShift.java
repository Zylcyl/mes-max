package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("md_shift")
public class MdShift {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shiftCode;
    private String shiftName;
    private String startTime;
    private String endTime;
    private Integer crossDay;
    private Integer breakMinutes;
    private Integer status;
}
