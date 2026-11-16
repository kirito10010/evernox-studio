package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.HyolActivity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 火影忍者OL 活动时间轴 Mapper
 */
@Mapper
public interface HyolActivityRepository extends BaseMapper<HyolActivity> {
}
