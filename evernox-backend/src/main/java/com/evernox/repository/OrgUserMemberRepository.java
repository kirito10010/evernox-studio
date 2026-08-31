package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.OrgUserMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织成员关系数据访问层
 */
@Mapper
public interface OrgUserMemberRepository extends BaseMapper<OrgUserMember> {
}
