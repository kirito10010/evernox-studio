package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ollama 模型（列表页）采集中间态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaModelDto {

    /** ollama 模型名(如 qwen3.6 / richardyoung/qwen3-8b-abliterated) */
    private String name;

    /** 命名空间(社区作者，官方为空) */
    private String namespace;

    /** 不含命名空间的模型名 */
    private String baseName;

    /** 详情页链接 */
    private String url;

    /** 模型介绍(英文原文) */
    private String description;

    /** 厂商key */
    private String vendor;

    /** 厂商中文名 */
    private String vendorLabel;

    /** 下载量(解析后数值) */
    private Long pulls;

    /** 下载量原文(如 6.7M) */
    private String pullsText;

    /** 变体数量 */
    private Integer tagCount;

    /** 能力标签 */
    private List<String> capabilities;

    /** 参数量集合(如 27b/35b) */
    private List<String> sizes;

    /** 是否 abliterated 版本 0/1 */
    private Integer isAbliterated;

    /** 官网更新时间(UTC) */
    private LocalDateTime sourceUpdatedAt;
}
