package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.SiteTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站点标签 Mapper
 */
@Mapper
public interface SiteTagRepository extends BaseMapper<SiteTag> {
}
