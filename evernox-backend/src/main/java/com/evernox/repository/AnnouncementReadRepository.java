package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AnnouncementRead;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告已读记录 Mapper
 */
@Mapper
public interface AnnouncementReadRepository extends BaseMapper<AnnouncementRead> {
}
