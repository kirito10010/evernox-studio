package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.OrgDistributionPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 一键发包方案 Mapper
 */
@Mapper
public interface OrgDistributionPlanRepository extends BaseMapper<OrgDistributionPlan> {
}
