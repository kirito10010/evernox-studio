package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.OrgOrganization;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织 Mapper
 */
@Mapper
public interface OrgOrganizationRepository extends BaseMapper<OrgOrganization> {
}
