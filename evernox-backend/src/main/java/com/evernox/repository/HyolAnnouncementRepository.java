package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.HyolAnnouncement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 火影忍者OL官方公告 Mapper
 */
@Mapper
public interface HyolAnnouncementRepository extends BaseMapper<HyolAnnouncement> {
}
