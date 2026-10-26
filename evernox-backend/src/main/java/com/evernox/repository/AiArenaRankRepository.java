package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AiArenaRank;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * Code Arena 模型排行榜仓储
 */
@Mapper
public interface AiArenaRankRepository extends BaseMapper<AiArenaRank> {

    /**
     * 库中最近一次落库时间。
     * 进程内的同步状态在重启后会丢失，状态接口用它作为「最近同步」的回退值。
     */
    @Select("SELECT MAX(synced_at) FROM ai_arena_rank")
    LocalDateTime selectLastSyncedAt();
}
