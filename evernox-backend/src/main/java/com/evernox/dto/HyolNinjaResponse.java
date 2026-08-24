package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 火影忍者OL忍者图鉴响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolNinjaResponse {

    private Long id;
    private String nid;
    private String name;
    private String nickname;
    private String attr;
    private String star;
    private String org;
    private String pos;
    private String getWay;
    private String effect;
    private String effectChase;
    private String avatarUrl;
    private String avatarUrl3;

    /** 按 奥义→普攻→被动1→被动2→被动3 顺序拼装的技能列表（缺失槽跳过） */
    private List<SkillItem> skills;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillItem {
        private String title;
        private String type;
        private String moment;
        private String desc;
        private String hurtType;
        private String chaseStatus;
        private String hurtStatus;
        private String rare;
        private String iconUrl;
    }
}
