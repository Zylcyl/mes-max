package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.entity.MdWorkCenter;
import com.mesmax.mapper.MdWorkCenterMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作中心组主数据。 */
@RestController
@RequestMapping("/md/workCenter")
public class MdWorkCenterController extends BaseCrudController<MdWorkCenter> {
    private final MdWorkCenterMapper mapper;

    public MdWorkCenterController(MdWorkCenterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<MdWorkCenter> mapper() {
        return mapper;
    }
}
