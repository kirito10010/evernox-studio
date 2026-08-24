package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AnnouncementImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告-插图关联 Mapper
 */
@Mapper
public interface AnnouncementImageRepository extends BaseMapper<AnnouncementImage> {
}
