package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AiOllamaTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * Ollama 模型库（变体标签）
 */
@Mapper
public interface AiOllamaTagRepository extends BaseMapper<AiOllamaTag> {
}
