package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Code Arena 抓取层中间对象（crawler 输出，尚未入库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaRankDto {

    /** 分类slug */
    private String category;

    /** 分类分组: category/domain */
    private String categoryGroup;

    /** 榜单页链接 */
    private String sourceUrl;

    /** 排名 */
    private Integer rank;

    /** 排名区间下限 */
    private Integer rankSpreadMin;

    /** 排名区间上限 */
    private Integer rankSpreadMax;

    /** 模型名 */
    private String modelName;

    /** 模型主页链接 */
    private String modelUrl;

    /** 开发方/厂商 */
    private String org;

    /** 许可类型 */
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
}
