package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 沙盘争霸地图全量响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMapResponse {

    private Long roundId;
    private Integer roundNo;
    /** 0未开始/1进行中/2已结算 */
    private Integer roundStatus;
    private LocalDateTime startAt;
    private LocalDateTime settleAt;
    private LocalDateTime endAt;

    /** 当前用户是否已加入本轮 */
    private Boolean joined;
    private Long baseCityId;
    private Integer force;
    private Integer actionPoints;
    private Integer ownedCityCount;
    /** 已完整占领的省数量 */
    private Integer completedProvinces;
    private Double powerScore;

    private List<CityState> cities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CityState {
        private Long id;
        private String adcode;
        private String name;
        private String province;
        private Double centerLng;
        private Double centerLat;
        private Integer weight;
        private Integer baseDefense;
        private Long ownerUserId;
        private Integer level;
        private Integer garrison;
    }
}
