package com.evernox.dto;

import com.evernox.entity.AiOllamaTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ollama 模型变体响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaTagResponse {

    private Long id;
    private String name;
    private String shortName;
    private String digest;
    private String sizeText;
    private Double sizeGb;
    private String contextText;
    private Integer contextTokens;
    private String inputs;
    private Integer isLatest;
    private Integer isMlx;
    private Integer isAbliterated;
    private String paramSize;
    private String command;
    private String sourceUpdatedText;

    public static OllamaTagResponse from(AiOllamaTag t) {
        return OllamaTagResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .shortName(t.getShortName())
                .digest(t.getDigest())
                .sizeText(t.getSizeText())
                .sizeGb(t.getSizeGb())
                .contextText(t.getContextText())
                .contextTokens(t.getContextTokens())
                .inputs(t.getInputs())
                .isLatest(t.getIsLatest())
                .isMlx(t.getIsMlx())
                .isAbliterated(t.getIsAbliterated())
                .paramSize(t.getParamSize())
                .command(t.getCommand())
                .sourceUpdatedText(t.getSourceUpdatedText())
                .build();
    }
}
