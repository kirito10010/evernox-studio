package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户下拉选项（筛选用，只暴露 ID 与用户名）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOptionResponse {

    private Long id;

    private String username;
}
