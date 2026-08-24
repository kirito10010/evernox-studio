package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.ExpenseCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消费类型 Mapper
 */
@Mapper
public interface ExpenseCategoryRepository extends BaseMapper<ExpenseCategory> {
}
