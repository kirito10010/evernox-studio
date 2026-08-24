package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.QuizQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 火影忍者OL测验题目 Mapper
 */
@Mapper
public interface QuizQuestionRepository extends BaseMapper<QuizQuestion> {
}
