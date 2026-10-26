package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一键发包方案请求（新建/修改共用，整体覆盖队列与必发）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgDistributionPlanRequest {

    @NotBlank(message = "方案名称不能为空")
    @Size(max = 100, message = "方案名称长度不能超过100字符")
    private String name;

    /** 排名依据字段 */
    private String rankField;

    private List<Item> items;

    private List<Forced> forced;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long packageId;
        private Integer quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Forced {
        private Long memberId;
        private Long packageId;
    }
}
