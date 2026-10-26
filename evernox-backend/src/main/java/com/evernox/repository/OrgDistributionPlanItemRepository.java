package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.OrgDistributionPlanItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 一键发包方案礼包队列 Mapper
 */
@Mapper
public interface OrgDistributionPlanItemRepository extends BaseMapper<OrgDistributionPlanItem> {
}
