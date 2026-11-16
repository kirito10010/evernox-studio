package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 忍者图鉴抓包导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolCodexImportResponse {

    private String build;

    private int ninjaCount;

    private int skillCount;

    private int formCount;

    private int imageCount;

    private List<String> warnings;
}
