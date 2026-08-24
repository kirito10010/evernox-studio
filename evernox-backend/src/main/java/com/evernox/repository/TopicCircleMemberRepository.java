package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.TopicCircleMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 话题圈关注 Mapper
 */
@Mapper
public interface TopicCircleMemberRepository extends BaseMapper<TopicCircleMember> {
}
