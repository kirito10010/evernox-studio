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
 * Ollama 模型库变体标签（关联 ai_ollama_model.id）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_ollama_tag")
public class AiOllamaTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属模型ID */
    private Long modelId;

    /** 完整标签名(如 qwen3.6:27b-coding) */
    private String name;

    /** 标签短名(如 27b-coding) */
    private String shortName;

    /** 摘要哈希 */
    private String digest;

    /** 大小原文(如 23GB) */
    private String sizeText;

    /** 大小(GB) */
    private Double sizeGb;

    /** 上下文原文(如 256K) */
    private String contextText;

    /** 上下文 token 数 */
    private Integer contextTokens;

    /** 输入类型(如 Text, Image) */
    private String inputs;

    /** 是否 latest */
    private Integer isLatest;

    /** 是否 MLX */
    private Integer isMlx;

    /** 是否 abliterated */
    private Integer isAbliterated;

    /** 参数量(如 27b) */
    private String paramSize;

    /** 下载命令 ollama run xxx */
    private String command;

    /** 官网更新时间(相对，如 3 weeks ago) */
    private String sourceUpdatedText;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
