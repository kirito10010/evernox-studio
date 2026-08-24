package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AnnouncementTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告标签 Mapper
 */
@Mapper
public interface AnnouncementTagRepository extends BaseMapper<AnnouncementTag> {
}
