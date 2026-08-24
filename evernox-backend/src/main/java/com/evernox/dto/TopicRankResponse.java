package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 话题集中营排行榜响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicRankResponse {

    /** 圈子热度排行（按帖子数） */
    private List<TopicCircleResponse> circles;

    /** 用户发帖排行 */
    private List<TopicUserRank> users;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicUserRank {
        private Long userId;
        private String username;
        private Integer postCount;
    }
}
