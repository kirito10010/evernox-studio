package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 每日访问趋势点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitTrendItem {

    private LocalDate date;

    private Long visits;

    private Long activeUsers;

    private Long logins;
}
