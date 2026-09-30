package com.mesmax.masterdata;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mesmax.common.BaseCrudController;
import com.mesmax.common.R;
import com.mesmax.entity.MdBom;
import com.mesmax.entity.MdBomItem;
import com.mesmax.mapper.MdBomItemMapper;
import com.mesmax.mapper.MdBomMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 产品 BOM 与其明细。 */
@RestController
@RequestMapping("/md/bom")
public class MdBomController extends BaseCrudController<MdBom> {
    private final MdBomMapper mapper;
    private final MdBomItemMapper itemMapper;

    public MdBomController(MdBomMapper mapper, MdBomItemMapper itemMapper) {
        this.mapper = mapper;
        this.itemMapper = itemMapper;
    }

    @Override
    protected BaseMapper<MdBom> mapper() {
        return mapper;
    }

    @GetMapping("/items/{bomId}")
    public R<List<MdBomItem>> items(@PathVariable Long bomId) {
        return R.ok(itemMapper.selectList(new QueryWrapper<MdBomItem>().eq("bom_id", bomId).orderByAsc("id")));
    }

    @PostMapping("/item/save")
    public R<MdBomItem> saveItem(@RequestBody MdBomItem item) {
        if (item.getId() == null) itemMapper.insert(item);
        else itemMapper.updateById(item);
        return R.ok("保存成功", item);
    }

    @DeleteMapping("/item/delete/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        itemMapper.deleteById(id);
        return R.ok("删除成功", null);
    }
}
