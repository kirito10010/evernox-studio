package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ollama 模型库筛选项（厂商 / 参数量尺寸 / 能力）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaFilterOptions {

    private List<VendorOption> vendors;
    private List<String> sizes;
    private List<String> capabilities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorOption {
        private String key;
        private String label;
        private Long count;
    }
}
