package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.TopicPostImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 话题帖子-图片关联 Mapper
 */
@Mapper
public interface TopicPostImageRepository extends BaseMapper<TopicPostImage> {
}
