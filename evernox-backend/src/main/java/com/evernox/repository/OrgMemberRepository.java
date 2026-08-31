package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.OrgMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织成员 Mapper
 */
@Mapper
public interface OrgMemberRepository extends BaseMapper<OrgMember> {
}
