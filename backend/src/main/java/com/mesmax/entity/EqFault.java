package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 设备故障台账。downMinutes / repairEndTime 用于统计 MTTR（平均修复时间）与 MTBF（平均故障间隔）。 */
@Data
@TableName("eq_fault")
public class EqFault {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String faultNo;
    private Long machineId;
    private String machineCode;
    /** 机械 / 电气 / 数控系统 / 液压 / 气动 / 刀具干涉 / 其他 */
    private String faultType;
    private String faultCode;
    private String faultDesc;
    private String reportUser;
    private LocalDateTime reportTime;
    private LocalDateTime respondTime;
    private LocalDateTime repairEndTime;
    private BigDecimal downMinutes;
    private String cause;
    private String solution;
    private String spareParts;
    private String repairUser;
    private String status;
    private String remark;
}
