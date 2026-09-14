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
 * 「致知」大模型智力追踪榜单条目（定期抓取入库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_zhizhi_rank")
public class AiZhizhiRank {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类：logic(推理)/code_v3(Agentic)/vision(视觉) */
    private String category;

    /** 榜单月份（YYYY-MM） */
    private String reportDate;

    /** 排名（按中位分数倒序） */
    private Integer rank;

    /** 模型名 */
    private String modelName;

    /** 极限分数 */
    private Double extremeScore;

    /** 中位分数 */
    private Double medianScore;

    /** 中位差距 */
    private String medianGap;

    /** 变更 */
    private String change;

    /** 平均耗时(秒) */
    private String avgTime;

    /** Token */
    private String token;

    /** 测试成本(元) */
    private String testCost;

    /** 价格(元/百万) */
    private String price;

    /** 发布时间 */
    private String releaseDate;

    /** 是否推理模型 0/1 */
    private Integer think;

    /** 模型国家：china/usa/other */
    private String country;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
