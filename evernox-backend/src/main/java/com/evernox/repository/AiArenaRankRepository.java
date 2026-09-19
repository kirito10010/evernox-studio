package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AiArenaRank;
import org.apache.ibatis.annotations.Mapper;

/**
 * Code Arena 模型排行榜仓储
 */
@Mapper
public interface AiArenaRankRepository extends BaseMapper<AiArenaRank> {
}
