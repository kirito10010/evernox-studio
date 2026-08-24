package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.TopicPostLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 话题帖子点赞 Mapper
 */
@Mapper
public interface TopicPostLikeRepository extends BaseMapper<TopicPostLike> {
}
