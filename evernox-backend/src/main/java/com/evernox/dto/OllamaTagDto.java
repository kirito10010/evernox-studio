package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ollama 模型变体（tags 页）采集中间态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaTagDto {

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

    /** 是否 latest 0/1 */
    private Integer isLatest;

    /** 是否 MLX 0/1 */
    private Integer isMlx;

    /** 是否 abliterated 0/1 */
    private Integer isAbliterated;

    /** 参数量(如 27b) */
    private String paramSize;

    /** 下载命令 ollama run xxx */
    private String command;

    /** 官网更新时间(相对，如 3 weeks ago) */
    private String sourceUpdatedText;
}
