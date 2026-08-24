package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 话题评论创建请求
 */
@Data
public class TopicCommentRequest {

    @NotNull(message = "帖子不能为空")
    private Long postId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论不能超过500字")
    private String content;
}
