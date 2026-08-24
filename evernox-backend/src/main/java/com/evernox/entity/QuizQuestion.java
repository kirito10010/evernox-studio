package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 火影忍者OL测验题目实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quiz_question")
public class QuizQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问题 */
    private String question;

    /** 归一化问题(去标点/空白/小写，用于查重) */
    private String normalizedQuestion;

    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;

    /** 正确答案(选项文本) */
    private String answer;

    /** 状态: 0待审批/1已通过/2已驳回 */
    private Integer status;

    /** 提交者用户ID(管理员添加为NULL) */
    private Long createdBy;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
