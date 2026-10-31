package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 火影忍者OL 喵问答题目实体。
 *
 * 与忍者测验的区别：带看图题的图片文件名，以及游戏内「已排除」的选项
 * （removed_options 存 JSON 数组字符串，最多 3 个）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("miao_question")
public class MiaoQuestion {

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

    /** 已排除选项(JSON 数组字符串，最多 3 个) */
    private String removedOptions;

    /** 题目图片文件名(存于 data/miao/images) */
    private String image;

    /** 状态: 0待审批/1已通过/2已驳回 */
    private Integer status;

    /** 提交者用户ID(管理员导入为NULL) */
    private Long createdBy;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
