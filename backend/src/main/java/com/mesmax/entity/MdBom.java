package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("md_bom")
public class MdBom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bomCode;
    private String bomName;
    private Long productMatId;
    private String version;
    private Integer status;
    private String remark;
}
