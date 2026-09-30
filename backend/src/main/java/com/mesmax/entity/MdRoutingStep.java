package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工艺路线工序明细 —— CNC 行业的差异化字段：
 * {@code ncProgramNo / ncProgramVer} 记录该工序应执行的 NC 程序号与版本，
 * 报工时与机床实际上报的程序号比对，防止「程序错版」导致批量报废。
 */
@Data
@TableName("md_routing_step")
public class MdRoutingStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routingId;
    private Integer seqNo;
    private Long procId;
    private String procCode;
    private String procName;
    private Long wcId;
    private String wcName;
    /** 准备工时（分钟/批次） */
    private BigDecimal setupTime;
    /** 单件工时（分钟/件） */
    private BigDecimal pieceTime;
    private String ncProgramNo;
    private String ncProgramVer;
    /** 关键工序：关键工序完工必须先出首件检验结果 */
    private Integer isKeyProc;
    private Integer isInspection;
    private String toolRequirement;
    private String remark;
}
