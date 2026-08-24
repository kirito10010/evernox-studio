package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.TopicPostFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 话题帖子收藏 Mapper
 */
@Mapper
public interface TopicPostFavoriteRepository extends BaseMapper<TopicPostFavorite> {
}
