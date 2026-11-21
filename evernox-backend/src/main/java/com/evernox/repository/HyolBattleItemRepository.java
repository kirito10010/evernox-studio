package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.HyolBattleItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 战场的召唤商品 Mapper
 */
@Mapper
public interface HyolBattleItemRepository extends BaseMapper<HyolBattleItem> {
}
