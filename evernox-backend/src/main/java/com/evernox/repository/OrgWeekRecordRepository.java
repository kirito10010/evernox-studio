package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.OrgWeekRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织每周成员记录 Mapper
 */
@Mapper
public interface OrgWeekRecordRepository extends BaseMapper<OrgWeekRecord> {
}
