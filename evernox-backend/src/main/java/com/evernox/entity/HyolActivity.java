package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 火影忍者OL 活动（从官方停机更新公告正文解析，无逻辑删除）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_activity")
public class HyolActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动名，如「仙人の宝库」 */
    private String name;

    /** 活动类型：返利/团购/宝库/总动员/签到/抽奖/礼包/折扣/其他 */
    private String category;

    /** 活动开始时间（解析不出来时为 null） */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 来源公告发布日期 */
    private LocalDate announceDate;

    /** 来源公告标题 */
    private String sourceTitle;

    /** 来源公告链接 */
    private String sourceUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
