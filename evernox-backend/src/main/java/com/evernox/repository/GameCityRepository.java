package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.GameCity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameCityRepository extends BaseMapper<GameCity> {
}
