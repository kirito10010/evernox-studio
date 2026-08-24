package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标签创建/更新请求（仅管理员）
 */
@Data
public class SiteTagRequest {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 30, message = "标签名不能超过30字")
    private String name;

    /** 排序权重，越小越前 */
    private Integer sort = 0;
}
