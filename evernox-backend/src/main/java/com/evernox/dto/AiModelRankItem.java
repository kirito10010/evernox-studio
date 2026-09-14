package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 模型排行榜条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelRankItem {

    /** 排名 */
    private Integer rank;

    /** 模型名 */
    private String modelName;

    /** 厂商 */
    private String provider;

    /** 上线日期 */
    private String releaseDate;

    /** 评测证据（如 "17 项评测"） */
    private String evidence;

    /** 置信度（如 "较充分"） */
    private String confidence;

    /** 输入价格（人民币 / 百万 Token） */
    private String inputPrice;

    /** 输出价格（人民币 / 百万 Token） */
    private String outputPrice;

    /** 共识指数 */
    private Double score;
}
