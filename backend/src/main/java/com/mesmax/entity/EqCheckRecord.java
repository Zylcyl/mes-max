package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eq_check_record")
public class EqCheckRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long machineId;
    private String machineCode;
    private Long itemId;
    private String itemName;
    private LocalDateTime checkTime;
    private String checker;
    private String shiftName;
    /** 正常 / 异常 */
    private String result;
    private String abnormalDesc;
    private String handleStatus;
    private LocalDateTime handleTime;
    private String remark;
}
