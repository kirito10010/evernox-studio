package com.evernox.dto;

import com.evernox.entity.ExpenseRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 消费记录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseRecordResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
    private String remark;
    private LocalDate expenseDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpenseRecordResponse from(ExpenseRecord record, String categoryName) {
        return ExpenseRecordResponse.builder()
                .id(record.getId())
                .categoryId(record.getCategoryId())
                .categoryName(categoryName)
                .amount(record.getAmount())
                .remark(record.getRemark())
                .expenseDate(record.getExpenseDate())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
