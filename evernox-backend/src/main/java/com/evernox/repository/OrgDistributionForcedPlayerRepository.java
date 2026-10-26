package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.OrgDistributionForcedPlayer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 一键发包必发玩家 Mapper
 */
@Mapper
public interface OrgDistributionForcedPlayerRepository extends BaseMapper<OrgDistributionForcedPlayer> {
}
