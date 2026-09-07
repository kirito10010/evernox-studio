package com.evernox.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 审批通过 / 调整标签请求
 *
 * 标签可选（可为空，表示无标签通过 / 改标签成无标签）。
 */
@Data
public class SiteReviewRequest {

    private List<Long> tagIds;

    /** 排序权重，越大越靠前，为空按 0 */
    @Min(value = 0, message = "权重不能为负数")
    private Integer weight;
}
