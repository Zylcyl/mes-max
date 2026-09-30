package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.entity.MdShift;
import com.mesmax.mapper.MdShiftMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 班次主数据。 */
@RestController
@RequestMapping("/md/shift")
public class MdShiftController extends BaseCrudController<MdShift> {
    private final MdShiftMapper mapper;

    public MdShiftController(MdShiftMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<MdShift> mapper() {
        return mapper;
    }
}
