package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.HyolBattleCamp;
import org.apache.ibatis.annotations.Mapper;

/**
 * 战场的召唤阵营 Mapper
 */
@Mapper
public interface HyolBattleCampRepository extends BaseMapper<HyolBattleCamp> {
}
