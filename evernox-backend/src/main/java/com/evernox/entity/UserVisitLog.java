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

import java.time.LocalDateTime;

/**
 * 平台访问日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_visit_log")
public class UserVisitLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 访问者ID */
    private Long userId;

    /** 用户名快照 */
    private String username;

    /** 事件类型: LOGIN登录 / VISIT访问 */
    private String type;

    /** 来源IP */
    private String ip;

    /** 浏览器/设备 */
    private String userAgent;

    /** 访问路径（仅 VISIT） */
    private String path;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
