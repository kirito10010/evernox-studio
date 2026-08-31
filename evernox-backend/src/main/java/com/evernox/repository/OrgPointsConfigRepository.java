package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.OrgPointsConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织积分换算比配置 Mapper
 */
@Mapper
public interface OrgPointsConfigRepository extends BaseMapper<OrgPointsConfig> {
}
