package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.UserPointsLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户积分流水数据访问层
 */
@Mapper
public interface UserPointsLogRepository extends BaseMapper<UserPointsLog> {
}
