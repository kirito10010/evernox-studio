package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.HyolMallItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商城特惠每日商品 Mapper
 */
@Mapper
public interface HyolMallItemRepository extends BaseMapper<HyolMallItem> {
}
