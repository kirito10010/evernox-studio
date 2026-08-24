package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.TopicComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 话题帖子评论 Mapper
 */
@Mapper
public interface TopicCommentRepository extends BaseMapper<TopicComment> {
}
