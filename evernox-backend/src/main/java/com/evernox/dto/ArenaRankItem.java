package com.evernox.dto;

import com.evernox.entity.AiArenaRank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Code Arena 榜单行响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaRankItem {

    private Long id;
    private String category;
    private Integer rank;
    private Integer rankSpreadMin;
    private Integer rankSpreadMax;
    private String modelName;
    private String modelUrl;
    private String org;
    private String license;
    private Integer score;
    private Integer scoreCiPlus;
    private Integer scoreCiMinus;
    private Integer votes;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private String contextText;
    private Long contextTokens;
    private Integer isPreliminary;
    private LocalDateTime syncedAt;

    public static ArenaRankItem from(AiArenaRank e) {
        return ArenaRankItem.builder()
                .id(e.getId())
                .category(e.getCategory())
                .rank(e.getRank())
                .rankSpreadMin(e.getRankSpreadMin())
                .rankSpreadMax(e.getRankSpreadMax())
                .modelName(e.getModelName())
                .modelUrl(e.getModelUrl())
                .org(e.getOrg())
                .license(e.getLicense())
                .score(e.getScore())
                .scoreCiPlus(e.getScoreCiPlus())
                .scoreCiMinus(e.getScoreCiMinus())
                .votes(e.getVotes())
                .inputPrice(e.getInputPrice())
                .outputPrice(e.getOutputPrice())
                .contextText(e.getContextText())
                .contextTokens(e.getContextTokens())
                .isPreliminary(e.getIsPreliminary())
                .syncedAt(e.getSyncedAt())
                .build();
    }
}
