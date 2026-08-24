package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 话题圈创建/更新请求
 */
@Data
public class TopicCircleRequest {

    @NotBlank(message = "圈子名称不能为空")
    @Size(max = 50, message = "圈子名称不能超过50字")
    private String name;

    @Size(max = 500, message = "圈子简介不能超过500字")
    private String description;
}
