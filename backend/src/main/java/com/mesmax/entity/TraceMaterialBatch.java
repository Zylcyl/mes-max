package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 毛坯/原材料批次。炉号（heat_no）与材质证明书是机加工追溯的合规重点。 */
@Data
@TableName("trace_material_batch")
public class TraceMaterialBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private Long matId;
    private String matCode;
    private String matName;
    private String spec;
    private String supplier;
    private String heatNo;
    private BigDecimal qty;
    private String unit;
    private String certNo;
    private LocalDateTime inTime;
    private String status;
    private String remark;
}
