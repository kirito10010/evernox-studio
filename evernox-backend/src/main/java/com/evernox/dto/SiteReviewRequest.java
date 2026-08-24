package com.evernox.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 审批通过 / 调整标签请求
 *
 * 需求要求公开前必须打标签，因此 tagIds 不允许为空。
 */
@Data
public class SiteReviewRequest {

    @NotEmpty(message = "请至少选择一个标签")
    private List<Long> tagIds;
}
