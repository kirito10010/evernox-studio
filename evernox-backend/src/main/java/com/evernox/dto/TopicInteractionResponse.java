package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 话题帖子互动（点赞/收藏）切换后的最新状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicInteractionResponse {

    private Boolean liked;
    private Integer likeCount;
    private Boolean favorited;
    private Integer favoriteCount;
}
