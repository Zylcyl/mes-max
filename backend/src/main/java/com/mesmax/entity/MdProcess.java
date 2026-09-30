package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("md_process")
public class MdProcess {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String procCode;
    private String procName;
    /** CUT 下料 / TURN 车 / MILL 铣 / DRILL 钻 / TAP 攻丝 / GRIND 磨 / WEDM 线切割 / EDM 电火花 / HEAT 热处理 / DEBURR 去毛刺 / INSPECT 检验 */
    private String procType;
    private Integer isInspection;
    private Integer isOutsource;
    private String defaultWcType;
    private Integer sortNo;
    private String remark;
}
