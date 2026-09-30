package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.entity.MdCustomer;
import com.mesmax.mapper.MdCustomerMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客户主数据。 */
@RestController
@RequestMapping("/md/customer")
public class MdCustomerController extends BaseCrudController<MdCustomer> {
    private final MdCustomerMapper mapper;

    public MdCustomerController(MdCustomerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<MdCustomer> mapper() {
        return mapper;
    }
}
