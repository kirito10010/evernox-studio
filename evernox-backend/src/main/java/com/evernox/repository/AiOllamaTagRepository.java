package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.dto.OllamaTagCount;
import com.evernox.entity.AiOllamaTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Ollama 模型库（变体标签）
 */
@Mapper
public interface AiOllamaTagRepository extends BaseMapper<AiOllamaTag> {

    /**
     * 每个模型已入库的变体数量。
     * 只做聚合，不加载整张表，避免同步时把全部变体实体拉进内存。
     */
    @Select("SELECT model_id, COUNT(*) AS total FROM ai_ollama_tag GROUP BY model_id")
    List<OllamaTagCount> countByModel();
}
