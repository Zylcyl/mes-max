package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("md_routing")
public class MdRouting {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String routingCode;
    private String routingName;
    private Long productMatId;
    private String version;
    private Integer status;
    private String remark;
}
