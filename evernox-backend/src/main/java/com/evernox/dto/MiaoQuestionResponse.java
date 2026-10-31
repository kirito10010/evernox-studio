package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 喵问答题目响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiaoQuestionResponse {

    private Long id;
    private String question;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String answer;

    /** 已排除选项 */
    private List<String> removedOptions;

    /** 题目图片文件名（前端拼 /api/miao/image/{image}） */
    private String image;

    private Integer status;

    /** 相似度 0~1，仅搜索时返回，列表为 null */
    private Double score;

    /** 当前用户已投的答案；匿名或未投票时为 null */
    private String myVote;

    private LocalDateTime createdAt;
}
