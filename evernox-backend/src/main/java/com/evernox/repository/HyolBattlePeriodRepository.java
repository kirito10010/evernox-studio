package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.HyolBattlePeriod;
import org.apache.ibatis.annotations.Mapper;

/**
 * 战场的召唤周期 Mapper
 */
@Mapper
public interface HyolBattlePeriodRepository extends BaseMapper<HyolBattlePeriod> {
}
