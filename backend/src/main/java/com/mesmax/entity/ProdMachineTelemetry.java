package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 机床遥测时序数据（CNC 特有量：主轴转速/进给倍率/主轴负载/各轴坐标/程序号/刀号/报警码）。 */
@Data
@TableName("prod_machine_telemetry")
public class ProdMachineTelemetry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long machineId;
    private String machineCode;
    private LocalDateTime ts;
    private BigDecimal spindleSpeed;
    private BigDecimal feedRate;
    private BigDecimal feedOverride;
    private BigDecimal spindleLoad;
    private BigDecimal axisX;
    private BigDecimal axisY;
    private BigDecimal axisZ;
    private String programNo;
    private String toolNo;
    private String alarmCode;
    private String runState;
    private Integer partCount;
    private BigDecimal powerKw;
    private Integer coolantOn;
}
