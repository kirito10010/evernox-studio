package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沙盘争霸排行项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRankItemDto {

    private Integer rank;
    private Long userId;
    private String username;
    private Integer ownedCityCount;
    private Integer force;
    private Double powerScore;
}
