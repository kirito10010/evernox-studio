package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.SalaryConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工资配置 Mapper
 */
@Mapper
public interface SalaryConfigRepository extends BaseMapper<SalaryConfig> {
}
