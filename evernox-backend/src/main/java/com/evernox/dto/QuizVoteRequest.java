package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 待确认题目的用户投票
 */
@Data
public class QuizVoteRequest {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    @NotBlank(message = "请选择一个答案")
    private String answer;
}
