package com.evernox.dto;

import com.evernox.entity.AiOllamaModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ollama 模型响应（列表 / 详情，详情时带 tags）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaModelResponse {

    private Long id;
    private String name;
    private String namespace;
    private String baseName;
    private String url;
    private String description;
    private String vendor;
    private String vendorLabel;
    private Long pulls;
    private String pullsText;
    private Integer tagCount;
    private String capabilities;
    private Integer isVision;
    private Integer isTools;
    private Integer isThinking;
    private Integer isEmbedding;
    private Integer isAbliterated;
    private String sizes;
    private Double minSizeB;
    private Double maxSizeB;
    private String defaultCommand;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;

    /** 变体列表（仅详情接口返回） */
    private List<OllamaTagResponse> tags;

    public static OllamaModelResponse from(AiOllamaModel m) {
        return OllamaModelResponse.builder()
                .id(m.getId())
                .name(m.getName())
                .namespace(m.getNamespace())
                .baseName(m.getBaseName())
                .url(m.getUrl())
                .description(m.getDescription())
                .vendor(m.getVendor())
                .vendorLabel(m.getVendorLabel())
                .pulls(m.getPulls())
                .pullsText(m.getPullsText())
                .tagCount(m.getTagCount())
                .capabilities(m.getCapabilities())
                .isVision(m.getIsVision())
                .isTools(m.getIsTools())
                .isThinking(m.getIsThinking())
                .isEmbedding(m.getIsEmbedding())
                .isAbliterated(m.getIsAbliterated())
                .sizes(m.getSizes())
                .minSizeB(m.getMinSizeB())
                .maxSizeB(m.getMaxSizeB())
                .defaultCommand(m.getDefaultCommand())
                .sourceUpdatedAt(m.getSourceUpdatedAt())
                .syncedAt(m.getSyncedAt())
                .build();
    }
}
