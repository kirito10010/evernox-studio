package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.UserVisitLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserVisitLogRepository extends BaseMapper<UserVisitLog> {
}
