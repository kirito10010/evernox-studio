package com.evernox.dto;

import com.evernox.entity.SalaryRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工资记录响应 DTO（预览与已保存记录共用，预览时 id 为空）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryRecordResponse {

    private Long id;
    private String month;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal attendanceDays;
    private BigDecimal actualAttendanceDays;
    private BigDecimal performanceDays;
    private BigDecimal performanceSalary;
    private BigDecimal overtimeDays;
    private BigDecimal overtimeSalary;
    private Integer lateMinutes;
    private BigDecimal attendanceRatio;
    private BigDecimal baseSalary;
    private BigDecimal postPerformance;
    private BigDecimal mealAllowance;
    private BigDecimal housingAllowance;
    private BigDecimal fullAttendanceBonus;
    private BigDecimal otherBonus;
    private BigDecimal pension;
    private BigDecimal medicalInsurance;
    private BigDecimal unemploymentInsurance;
    private BigDecimal totalSalary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SalaryRecordResponse from(SalaryRecord r) {
        return SalaryRecordResponse.builder()
                .id(r.getId())
                .month(r.getMonth())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .attendanceDays(r.getAttendanceDays())
                .actualAttendanceDays(r.getActualAttendanceDays())
                .performanceDays(r.getPerformanceDays())
                .performanceSalary(r.getPerformanceSalary())
                .overtimeDays(r.getOvertimeDays())
                .overtimeSalary(r.getOvertimeSalary())
                .lateMinutes(r.getLateMinutes())
                .attendanceRatio(r.getAttendanceRatio())
                .baseSalary(r.getBaseSalary())
                .postPerformance(r.getPostPerformance())
                .mealAllowance(r.getMealAllowance())
                .housingAllowance(r.getHousingAllowance())
                .fullAttendanceBonus(r.getFullAttendanceBonus())
                .otherBonus(r.getOtherBonus())
                .pension(r.getPension())
                .medicalInsurance(r.getMedicalInsurance())
                .unemploymentInsurance(r.getUnemploymentInsurance())
                .totalSalary(r.getTotalSalary())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
