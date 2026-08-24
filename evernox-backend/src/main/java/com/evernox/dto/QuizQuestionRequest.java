package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 火影忍者OL测验题目创建/提交请求
 */
@Data
public class QuizQuestionRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过500字")
    private String question;

    @NotBlank(message = "选项A不能为空")
    @Size(max = 200, message = "选项A不能超过200字")
    private String optionA;

    @NotBlank(message = "选项B不能为空")
    @Size(max = 200, message = "选项B不能超过200字")
    private String optionB;

    @NotBlank(message = "选项C不能为空")
    @Size(max = 200, message = "选项C不能超过200字")
    private String optionC;

    @NotBlank(message = "选项D不能为空")
    @Size(max = 200, message = "选项D不能超过200字")
    private String optionD;

    @NotBlank(message = "答案不能为空")
    @Size(max = 200, message = "答案不能超过200字")
    private String answer;
}
