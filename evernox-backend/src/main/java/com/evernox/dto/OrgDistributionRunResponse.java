package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一键发包执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgDistributionRunResponse {

    private String planName;

    /** 实际发放总数 */
    private int assignedCount;

    /** 其中必发人数 */
    private int forcedCount;

    /** 其中排名发放人数 */
    private int rankedCount;

    /** 因忍战次数为 0 / 未录入而跳过的人数 */
    private int skippedZeroBattleCount;

    /** 被跳过的必发玩家（成员不存在 / 零忍战 / 名额已满等） */
    private List<String> skippedForcedNames;

    /** 各礼包实际发放数量 */
    private List<PackageSummary> packageSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageSummary {
        private String packageName;
        private int count;
    }
}
