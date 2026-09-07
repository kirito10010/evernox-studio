package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.GameRound;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameRoundRepository extends BaseMapper<GameRound> {
}
