package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ollama 变体按模型分组的数量统计（同步时用于判断哪些模型缺少变体数据）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaTagCount {

    private Long modelId;
    private Long total;
}
