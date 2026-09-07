package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.GameReward;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameRewardRepository extends BaseMapper<GameReward> {
}
