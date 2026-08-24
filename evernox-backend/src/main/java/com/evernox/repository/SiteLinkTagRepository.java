package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.SiteLinkTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站点-标签关联 Mapper
 */
@Mapper
public interface SiteLinkTagRepository extends BaseMapper<SiteLinkTag> {
}
