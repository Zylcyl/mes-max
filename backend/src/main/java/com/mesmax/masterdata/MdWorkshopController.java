package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.entity.MdWorkshop;
import com.mesmax.mapper.MdWorkshopMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 车间主数据。 */
@RestController
@RequestMapping("/md/workshop")
public class MdWorkshopController extends BaseCrudController<MdWorkshop> {
    private final MdWorkshopMapper mapper;

    public MdWorkshopController(MdWorkshopMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<MdWorkshop> mapper() {
        return mapper;
    }
}
