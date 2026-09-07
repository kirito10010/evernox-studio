package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 活跃用户排行项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitUserRankItem {

    private Long userId;
    private String username;
    private Long visitCount;
    private LocalDateTime lastVisitAt;
}
