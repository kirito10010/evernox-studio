package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站点-标签关联实体（复合主键，风格对齐 ImageAlbum）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("site_link_tag")
public class SiteLinkTag {

    /** 站点ID */
    @TableId(value = "site_id")
    private Long siteId;

    /** 标签ID */
    private Long tagId;

    private LocalDateTime createdAt;
}
