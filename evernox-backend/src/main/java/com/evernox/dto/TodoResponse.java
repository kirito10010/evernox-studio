package com.evernox.dto;

import com.evernox.entity.Todo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办响应 DTO
 *
 * overdue 由服务端按当天日期算好再下发：客户端时钟不可信，也免得前端各处重复判断。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoResponse {

    private Long id;
    private String content;
    private Integer done;
    private Integer priority;
    private LocalDate dueDate;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 是否已逾期（未完成且截止日期早于今天） */
    private Boolean overdue;

    public static TodoResponse from(Todo todo, LocalDate today) {
        boolean overdue = todo.getDone() != null && todo.getDone() == 0
                && todo.getDueDate() != null
                && todo.getDueDate().isBefore(today);
        return TodoResponse.builder()
                .id(todo.getId())
                .content(todo.getContent())
                .done(todo.getDone())
                .priority(todo.getPriority())
                .dueDate(todo.getDueDate())
                .finishedAt(todo.getFinishedAt())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .overdue(overdue)
                .build();
    }
}
