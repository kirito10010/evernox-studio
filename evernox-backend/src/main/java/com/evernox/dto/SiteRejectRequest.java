package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批驳回请求：原因对用户可见，必须填写
 */
@Data
public class SiteRejectRequest {

    @NotBlank(message = "请填写驳回原因")
    @Size(max = 500, message = "驳回原因不能超过500字")
    private String reason;
}
