package com.evernox.codex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 忍者图鉴数据集（落盘为 {@code ninjas.json}，前端直接消费）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodexDataset {

    /** 数据集结构版本，前端据此判断是否支持新字段。 */
    private int version;

    /** 客户端构建号，如 {@code NarutoBeta11.37Build301}。 */
    private String build;

    /** 导入时间（ISO-8601）。 */
    private String generatedAt;

    private Stats stats;

    /** 词条名 → 解释列表（来自 SkillKeyDescCFG）。 */
    private Map<String, List<String>> keywords;

    /** 技能键 → 技能正文。 */
    private Map<String, CodexSkill> skills;

    private List<CodexNinja> ninjas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private int ninjas;
        private int skills;
        private int forms;
        private int slots;
        @JsonProperty("break")
        private int breakCount;
        private int y;
        private int l;
        private int extra;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodexNinja {
        private String id;
        private String name;
        private int star;
        private int rare;
        /** 站点静态立绘路径：{@code portraits/{id}.png}。 */
        private String portrait;
        /** 站点静态头像路径：{@code heads/{id}.png}。 */
        private String head;
        private int breakthroughStar;
        /** 属性：水 / 火 / 风 / 雷 / 土。 */
        private String attribute;
        /** 官方标签（来自 NinjaInfoCFG.showPropertys，如 木叶 / 宇智波一族 / 人柱力 / 男性）。 */
        private List<String> tags;
        private Flags flags;
        private List<CodexForm> forms;
        /** 额外形态（同忍者 ID+10 的槽位组），没有则为空数组。 */
        private List<CodexForm> extraForms;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Flags {
        private boolean star;
        @JsonProperty("break")
        private boolean hasBreak;
        private boolean y;
        private boolean l;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodexForm {
        private String key;
        private String label;
        private int star;
        /** {@code base} / {@code star} / {@code extra}。 */
        private String kind;
        private List<CodexSlot> slots;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodexSlot {
        private String role;
        private String initial;
        /** 突破阶梯（按突破顺序）。 */
        @JsonProperty("break")
        private List<String> breakSkills;
        private String y;
        private String l;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodexSkill {
        private String id;
        private String name;
        private String role;
        private String desc;
        private int cd;
        private int enterCd;
        private int mp;
        /** 静态图标路径：{@code skills/{iconId}.png}。 */
        private String icon;
        /** 图标兜底链：{@link #icon} 404 时依次尝试（客户端没有打包部分技能图标）。 */
        @JsonProperty("iconFallback")
        private List<String> iconFallbacks;
        private boolean immediately;
        /** 状态词条名（解释在顶层 keywords 里）。 */
        private List<String> states;
    }
}
