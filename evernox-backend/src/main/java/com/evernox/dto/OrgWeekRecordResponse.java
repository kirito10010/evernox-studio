package com.evernox.dto;

import com.evernox.entity.OrgWeekRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 组织每周成员记录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgWeekRecordResponse {

    private Long id;
    private Long organizationId;
    private String organizationName;
    private LocalDate weekDate;
    private Long memberId;
    private String memberName;
    private String position;
    private Integer ninjaBattleCount;
    private Integer totalPower;
    private Integer powerIncrease;
    private Integer copperContribution;
    private Integer beastSacrifice;
    private Integer renegadeCount;
    private Integer isRenegadeLeader;
    private BigDecimal lastWeekPoints;
    private BigDecimal thisWeekPoints;
    private BigDecimal totalPoints;
    private BigDecimal deductionRatio;
    private BigDecimal pointsAfterDeduction;
    private Long rewardPackageId;
    private String rewardPackageName;

    public static OrgWeekRecordResponse from(OrgWeekRecord r) {
        return OrgWeekRecordResponse.builder()
                .id(r.getId())
                .organizationId(r.getOrganizationId())
                .organizationName(r.getOrganizationName())
                .weekDate(r.getWeekDate())
                .memberId(r.getMemberId())
                .memberName(r.getMemberName())
                .position(r.getPosition())
                .ninjaBattleCount(r.getNinjaBattleCount())
                .totalPower(r.getTotalPower())
                .powerIncrease(r.getPowerIncrease())
                .copperContribution(r.getCopperContribution())
                .beastSacrifice(r.getBeastSacrifice())
                .renegadeCount(r.getRenegadeCount())
                .isRenegadeLeader(r.getIsRenegadeLeader())
                .lastWeekPoints(r.getLastWeekPoints())
                .thisWeekPoints(r.getThisWeekPoints())
                .totalPoints(r.getTotalPoints())
                .deductionRatio(r.getDeductionRatio())
                .pointsAfterDeduction(r.getPointsAfterDeduction())
                .rewardPackageId(r.getRewardPackageId())
                .rewardPackageName(r.getRewardPackageName())
                .build();
    }
}
