package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 网站分享创建/更新请求
 */
@Data
public class SiteLinkRequest {

    /** 网站名称 */
    @NotBlank(message = "网站名称不能为空")
    @Size(max = 100, message = "网站名称不能超过100字")
    private String title;

    /** 网站链接：只放行 http/https，挡住 javascript:、data: 等可执行协议 */
    @NotBlank(message = "网站链接不能为空")
    @Size(max = 500, message = "网站链接不能超过500字")
    @Pattern(regexp = "^https?://\\S+$", message = "链接必须以 http:// 或 https:// 开头")
    private String url;

    /** 网站详情介绍 */
    @Size(max = 2000, message = "详情介绍不能超过2000字")
    private String description;

    /** 封面图片ID，须为当前用户自己上传的图片 */
    private Long coverImageId;
}
