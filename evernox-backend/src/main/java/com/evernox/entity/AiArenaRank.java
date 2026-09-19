package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Code Arena 模型排行榜（数据源 arena.ai，按分类存全量榜）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_arena_rank")
public class AiArenaRank {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类slug: overall/fullstack/frontend/html/react/... */
    private String category;

    /** 分类分组: category/domain */
    private String categoryGroup;

    /** 排名 */
    private Integer rank;

    /** 排名区间下限 */
    private Integer rankSpreadMin;

    /** 排名区间上限 */
    private Integer rankSpreadMax;

    /** 模型名(英文原文) */
    private String modelName;

    /** 模型主页链接 */
    private String modelUrl;

    /** 开发方/厂商(英文原文) */
    private String org;

    /** 许可类型(英文原文) */
    private String license;

    /** Arena 分数 */
    private Integer score;

    /** 分数置信区间 + */
    private Integer scoreCiPlus;

    /** 分数置信区间 - */
    private Integer scoreCiMinus;

    /** 投票数 */
    private Integer votes;

    /** 输入价格(美元/百万token) */
    private BigDecimal inputPrice;

    /** 输出价格(美元/百万token) */
    private BigDecimal outputPrice;

    /** 上下文原文(如 262.1K) */
    private String contextText;

    /** 上下文token数 */
    private Long contextTokens;

    /** 是否 Preliminary 0/1 */
    private Integer isPreliminary;

    /** 榜单页链接 */
    private String sourceUrl;

    /** 最近同步时间 */
    private LocalDateTime syncedAt;

    /** 入库时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
