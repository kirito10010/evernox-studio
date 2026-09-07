package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沙盘争霸地图变更事件（SSE 广播）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMapChangeEvent {

    private Long roundId;
    private Long cityId;
    /** 变更后归属（0 表示无主） */
    private Long ownerUserId;
    private Integer level;
    private Integer garrison;
}
