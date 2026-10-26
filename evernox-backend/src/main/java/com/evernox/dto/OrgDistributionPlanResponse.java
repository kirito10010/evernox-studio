package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一键发包方案响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgDistributionPlanResponse {

    private Long id;
    private Long organizationId;
    private String name;
    private String rankField;
    private List<Item> items;
    private List<Forced> forced;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private Long packageId;
        private String packageName;
        private Integer quantity;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Forced {
        private Long id;
        private Long memberId;
        private String memberName;
        private Long packageId;
        private String packageName;
    }
}
