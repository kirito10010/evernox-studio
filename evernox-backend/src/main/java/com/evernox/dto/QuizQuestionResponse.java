package com.evernox.dto;

import com.evernox.entity.QuizQuestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 火影忍者OL测验题目响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionResponse {

    private Long id;
    private String question;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String answer;
    private Integer status;

    /** 相似度 0~1，仅搜索时返回，列表为 null */
    private Double score;

    private LocalDateTime createdAt;

    public static QuizQuestionResponse from(QuizQuestion q) {
        return QuizQuestionResponse.builder()
                .id(q.getId())
                .question(q.getQuestion())
                .optionA(q.getOptionA())
                .optionB(q.getOptionB())
                .optionC(q.getOptionC())
                .optionD(q.getOptionD())
                .answer(q.getAnswer())
                .status(q.getStatus())
                .createdAt(q.getCreatedAt())
                .build();
    }
}
