package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 超级会员卡密响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionCodeResponse {

    private Long id;
    private String code;
    private Integer days;
    /** 0未使用/1已使用 */
    private Integer status;
    private Long usedBy;
    private String username;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
