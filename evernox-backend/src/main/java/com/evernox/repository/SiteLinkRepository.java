package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.SiteLink;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网站分享 Mapper
 */
@Mapper
public interface SiteLinkRepository extends BaseMapper<SiteLink> {
}
