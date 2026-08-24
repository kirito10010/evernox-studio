package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告标签创建/更新请求
 */
@Data
public class AnnouncementTagRequest {

    /** 标签名 */
    @NotBlank(message = "标签名不能为空")
    @Size(max = 30, message = "标签名不能超过30字")
    private String name;

    /** 标签颜色(HEX，如 #409EFF) */
    @NotBlank(message = "标签颜色不能为空")
    @Size(max = 20, message = "标签颜色格式不正确")
    private String color;
}
