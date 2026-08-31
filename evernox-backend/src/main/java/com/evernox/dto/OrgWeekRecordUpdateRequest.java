package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 周记录手动编辑请求（活动数据字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgWeekRecordUpdateRequest {

    private Integer ninjaBattleCount;
    private Integer totalPower;
    private Integer powerIncrease;
    private Integer copperContribution;
    private Integer beastSacrifice;
    private Integer renegadeCount;
    private Integer isRenegadeLeader;
}
