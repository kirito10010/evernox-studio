package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 模型排行榜条目（定期抓取入库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_model_rank")
public class AiModelRank {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类：overall/coding/reasoning/knowledge/professional */
    private String category;

    /** 排名 */
    private Integer rank;

    /** 模型名 */
    private String modelName;

    /** 厂商 */
    private String provider;

    /** 上线日期 */
    private String releaseDate;

    /** 评测证据 */
    private String evidence;

    /** 置信度 */
    private String confidence;

    /** 输入价格 */
    private String inputPrice;

    /** 输出价格 */
    private String outputPrice;

    /** 共识指数 */
    private Double score;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
