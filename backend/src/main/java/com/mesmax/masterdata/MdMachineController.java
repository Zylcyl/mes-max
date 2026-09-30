package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.entity.MdMachine;
import com.mesmax.mapper.MdMachineMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 机床（CNC 设备）主数据。 */
@RestController
@RequestMapping("/md/machine")
public class MdMachineController extends BaseCrudController<MdMachine> {
    private final MdMachineMapper mapper;

    public MdMachineController(MdMachineMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<MdMachine> mapper() {
        return mapper;
    }
}
