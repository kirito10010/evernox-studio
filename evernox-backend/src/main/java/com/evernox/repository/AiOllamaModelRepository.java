package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AiOllamaModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Ollama 模型库（模型族）
 */
@Mapper
public interface AiOllamaModelRepository extends BaseMapper<AiOllamaModel> {

    /**
     * 库中最近一次落库时间。
     * 进程内的同步状态在重启后会丢失，状态接口用它作为「最近同步」的回退值。
     */
    @Select("SELECT MAX(synced_at) FROM ai_ollama_model")
    LocalDateTime selectLastSyncedAt();

    /**
     * 一条 SQL 刷新全部模型的最近同步时间。
     * 比起逐个 updateById，只产生一次数据库往返；用于「内容没变化」的模型。
     */
    @Update("UPDATE ai_ollama_model SET synced_at = #{now}")
    int touchAll(@Param("now") LocalDateTime now);
}
