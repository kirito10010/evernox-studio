package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.GamePlayer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GamePlayerRepository extends BaseMapper<GamePlayer> {
}
