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
 * Ollama 模型库（模型族，仅本地可下载模型）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_ollama_model")
public class AiOllamaModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ollama 模型名(如 qwen3.6 / richardyoung/qwen3-8b-abliterated) */
    private String name;

    /** 命名空间(社区作者，官方为空) */
    private String namespace;

    /** 不含命名空间的模型名 */
    private String baseName;

    /** ollama 详情页链接 */
    private String url;

    /** 模型介绍(英文原文) */
    private String description;

    /** 厂商key: qwen/deepseek/.../community/other */
    private String vendor;

    /** 厂商中文名 */
    private String vendorLabel;

    /** 下载量(解析后数值) */
    private Long pulls;

    /** 下载量原文(如 6.7M) */
    private String pullsText;

    /** 变体数量 */
    private Integer tagCount;

    /** 能力标签,逗号分隔: vision,tools,thinking */
    private String capabilities;

    /** 是否 vision */
    private Integer isVision;

    /** 是否 tools */
    private Integer isTools;

    /** 是否 thinking */
    private Integer isThinking;

    /** 是否 embedding */
    private Integer isEmbedding;

    /** 是否 abliterated 去审查版本 */
    private Integer isAbliterated;

    /** 参数量集合,逗号分隔升序: 1b,7b,27b */
    private String sizes;

    /** 最小参数量(十亿) */
    private Double minSizeB;

    /** 最大参数量(十亿) */
    private Double maxSizeB;

    /** 默认下载命令 ollama run xxx */
    private String defaultCommand;

    /** 官网更新时间(UTC) */
    private LocalDateTime sourceUpdatedAt;

    /** 最近一次同步时间 */
    private LocalDateTime syncedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
