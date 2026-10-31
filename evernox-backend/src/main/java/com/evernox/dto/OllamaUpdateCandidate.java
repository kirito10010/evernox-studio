package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 「更新模型」候选列表项：管理员筛选后勾选要更新的模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaUpdateCandidate {

    private Long id;
    private String name;
    private String category;
    private String vendorLabel;
    private String pullsText;
    private Integer tagCount;
    private LocalDateTime lastUpdatedAt;
}
