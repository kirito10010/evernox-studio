package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 战场的召唤阵营响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolBattleCampResponse {

    /** 阵营名称（同时是该阵营的货币名） */
    private String name;

    /** 代表忍者 */
    private String representative;
}
