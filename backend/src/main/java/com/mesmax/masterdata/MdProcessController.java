package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.entity.MdProcess;
import com.mesmax.mapper.MdProcessMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工序主数据。 */
@RestController
@RequestMapping("/md/process")
public class MdProcessController extends BaseCrudController<MdProcess> {
    private final MdProcessMapper mapper;

    public MdProcessController(MdProcessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<MdProcess> mapper() {
        return mapper;
    }
}
