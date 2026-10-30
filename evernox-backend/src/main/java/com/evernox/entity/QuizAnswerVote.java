package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 待确认题目的用户答案投票。
 *
 * 每个用户对每道题只能投一次；同一答案达到 2 票时写入题目答案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quiz_answer_vote")
public class QuizAnswerVote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long userId;

    /** 用户选择的选项文本 */
    private String answer;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
