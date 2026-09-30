package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.entity.MdEmployee;
import com.mesmax.mapper.MdEmployeeMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 员工主数据（含技能等级与班组，用于派工与人机匹配）。 */
@RestController
@RequestMapping("/md/employee")
public class MdEmployeeController extends BaseCrudController<MdEmployee> {
    private final MdEmployeeMapper mapper;

    public MdEmployeeController(MdEmployeeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<MdEmployee> mapper() {
        return mapper;
    }
}
