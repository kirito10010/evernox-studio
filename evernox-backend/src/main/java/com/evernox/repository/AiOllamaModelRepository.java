package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AiOllamaModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * Ollama 模型库（模型族）
 */
@Mapper
public interface AiOllamaModelRepository extends BaseMapper<AiOllamaModel> {
}
