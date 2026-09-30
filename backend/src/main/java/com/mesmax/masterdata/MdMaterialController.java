package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.entity.MdMaterial;
import com.mesmax.mapper.MdMaterialMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 物料主数据（毛坯 / 半成品 / 成品 / 标准件）。 */
@RestController
@RequestMapping("/md/material")
public class MdMaterialController extends BaseCrudController<MdMaterial> {
    private final MdMaterialMapper mapper;

    public MdMaterialController(MdMaterialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<MdMaterial> mapper() {
        return mapper;
    }
}
