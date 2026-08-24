package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.PerformanceProject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产项目配置 Mapper
 */
@Mapper
public interface PerformanceProjectRepository extends BaseMapper<PerformanceProject> {
}
