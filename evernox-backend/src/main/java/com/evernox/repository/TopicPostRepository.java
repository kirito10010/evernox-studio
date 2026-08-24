package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.TopicPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 话题帖子 Mapper
 */
@Mapper
public interface TopicPostRepository extends BaseMapper<TopicPost> {

    /** 用户发帖排行（按帖子数倒序取前 N） */
    @Select("SELECT user_id, COUNT(*) AS cnt FROM topic_post WHERE deleted = 0 GROUP BY user_id ORDER BY cnt DESC, user_id ASC LIMIT #{limit}")
    List<Map<String, Object>> selectTopPosters(@Param("limit") int limit);
}
