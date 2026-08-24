package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.SalaryRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工资记录 Mapper
 */
@Mapper
public interface SalaryRecordRepository extends BaseMapper<SalaryRecord> {
}
