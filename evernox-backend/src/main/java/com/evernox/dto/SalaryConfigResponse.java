package com.evernox.dto;

import com.evernox.entity.SalaryConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工资配置响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryConfigResponse {

    private Long id;
    private Long userId;
    private BigDecimal baseSalary;
    private BigDecimal postPerformance;
    private BigDecimal mealAllowance;
    private BigDecimal housingAllowance;
    private BigDecimal fullAttendanceBonus;
    private BigDecimal otherBonus;
    private BigDecimal pension;
    private BigDecimal medicalInsurance;
    private BigDecimal unemploymentInsurance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalaryConfigResponse from(SalaryConfig c) {
        return SalaryConfigResponse.builder()
                .id(c.getId())
                .userId(c.getUserId())
                .baseSalary(c.getBaseSalary())
                .postPerformance(c.getPostPerformance())
                .mealAllowance(c.getMealAllowance())
                .housingAllowance(c.getHousingAllowance())
                .fullAttendanceBonus(c.getFullAttendanceBonus())
                .otherBonus(c.getOtherBonus())
                .pension(c.getPension())
                .medicalInsurance(c.getMedicalInsurance())
                .unemploymentInsurance(c.getUnemploymentInsurance())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
