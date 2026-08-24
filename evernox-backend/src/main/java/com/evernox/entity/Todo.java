package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办事项实体
 *
 * 纯私有数据，没有审批流与公开状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("todo")
public class Todo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 待办内容 */
    private String content;

    /** 0未完成/1已完成 */
    private Integer done;

    /** 优先级: 0低/1中/2高 */
    private Integer priority;

    /** 截止日期，只到天，避免时区问题 */
    private LocalDate dueDate;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
