package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 喵问答题目创建/提交/编辑请求
 */
@Data
public class MiaoQuestionRequest {

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

    @Size(max = 200, message = "答案不能超过200字")
    private String answer;

    /** 已排除选项（最多 3 个，必须属于四个选项） */
    private List<String> removedOptions;

    /** 题目图片文件名（用户提交不使用） */
    private String image;
}
