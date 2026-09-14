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
 * AI 编程资讯收藏
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_news_favorite")
public class AiNewsFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 资讯条目 ID */
    private Long itemId;

    /** 收藏时间 */
    private LocalDateTime createdAt;
}
