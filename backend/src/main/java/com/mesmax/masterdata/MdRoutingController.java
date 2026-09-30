package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.R;
import com.mesmax.entity.MdRouting;
import com.mesmax.entity.MdRoutingStep;
import com.mesmax.mapper.MdRoutingMapper;
import com.mesmax.mapper.MdRoutingStepMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 工艺路线与其工序明细（含 NC 程序号/版本）。 */
@RestController
@RequestMapping("/md/routing")
public class MdRoutingController extends BaseCrudController<MdRouting> {
    private final MdRoutingMapper mapper;
    private final MdRoutingStepMapper stepMapper;

    public MdRoutingController(MdRoutingMapper mapper, MdRoutingStepMapper stepMapper) {
        this.mapper = mapper;
        this.stepMapper = stepMapper;
    }

    @Override
    protected BaseMapper<MdRouting> mapper() {
        return mapper;
    }

    @GetMapping("/steps/{routingId}")
    public R<List<MdRoutingStep>> steps(@PathVariable Long routingId) {
        return R.ok(stepMapper.selectList(new QueryWrapper<MdRoutingStep>()
                .eq("routing_id", routingId).orderByAsc("seq_no")));
    }

    @PostMapping("/step/save")
    public R<MdRoutingStep> saveStep(@RequestBody MdRoutingStep step) {
        if (step.getId() == null) stepMapper.insert(step);
        else stepMapper.updateById(step);
        return R.ok("保存成功", step);
    }

    @DeleteMapping("/step/delete/{id}")
    public R<Void> deleteStep(@PathVariable Long id) {
        stepMapper.deleteById(id);
        return R.ok("删除成功", null);
    }
}
