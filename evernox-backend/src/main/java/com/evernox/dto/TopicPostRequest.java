package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 话题帖子创建/更新请求
 */
@Data
public class TopicPostRequest {

    @NotNull(message = "圈子不能为空")
    private Long circleId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过100字")
    private String title;

    /** 正文 HTML（插图以 <img data-image-id="..."> 形式出现） */
    @Size(max = 200_000, message = "正文过长")
    private String content;
}
