-- EverNox Database Schema
-- 永夜照相馆数据库初始化脚本
-- 应用启动时自动执行（spring.sql.init.mode=always）

CREATE DATABASE IF NOT EXISTS evernox_backend DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE evernox_backend;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(Argon2id加密)',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `role` VARCHAR(20) NOT NULL DEFAULT 'member' COMMENT '角色: admin/super_member/member',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1激活/0禁用',
    `points` INT NOT NULL DEFAULT 0 COMMENT '积分',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_at` DATETIME NULL COMMENT '最后登录时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_role` (`role`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 图片表
CREATE TABLE IF NOT EXISTS `image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '图片ID',
    `user_id` BIGINT NOT NULL COMMENT '上传者用户ID',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `storage_path` VARCHAR(500) NOT NULL COMMENT '编码后文件存储路径(.evx)',
    `thumbnail_path` VARCHAR(500) NULL COMMENT '缩略图存储路径',
    `thumbnail_iv` VARCHAR(32) NULL COMMENT '缩略图编码 IV(Hex)',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '原始文件大小(bytes)',
    `mime_type` VARCHAR(50) NOT NULL COMMENT 'MIME类型(服务端按魔数识别)',
    `width` INT NULL COMMENT '图片宽度',
    `height` INT NULL COMMENT '图片高度',
    `iv` VARCHAR(32) NOT NULL COMMENT '服务端编码 IV(Hex)',
    `visibility` TINYINT NOT NULL DEFAULT 0 COMMENT '0私密/1公开',
    `purpose` TINYINT NOT NULL DEFAULT 0 COMMENT '用途: 0图床照片/1相册封面/2网站分享封面',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_visibility` (`visibility`),
    KEY `idx_purpose` (`purpose`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片表';

-- 相册表
CREATE TABLE IF NOT EXISTS `album` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '相册ID',
    `user_id` BIGINT NOT NULL COMMENT '创建者用户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '相册名称',
    `description` TEXT NULL COMMENT '相册描述',
    `cover_image_id` BIGINT NULL COMMENT '封面图片ID',
    `visibility` TINYINT NOT NULL DEFAULT 0 COMMENT '0私密/1公开',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_visibility` (`visibility`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='相册表';

-- 图片-相册关联表
CREATE TABLE IF NOT EXISTS `image_album` (
    `image_id` BIGINT NOT NULL COMMENT '图片ID',
    `album_id` BIGINT NOT NULL COMMENT '相册ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`image_id`, `album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片-相册关联表';

-- 网站分享表
CREATE TABLE IF NOT EXISTS `site_link` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '站点ID',
    `user_id` BIGINT NOT NULL COMMENT '分享者用户ID',
    `title` VARCHAR(100) NOT NULL COMMENT '网站名称',
    `url` VARCHAR(500) NOT NULL COMMENT '网站链接(仅 http/https)',
    `description` TEXT NULL COMMENT '网站详情介绍',
    `cover_image_id` BIGINT NULL COMMENT '封面图片ID(image.id)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0私有/1待审批/2已公开/3已驳回',
    `reject_reason` VARCHAR(500) NULL COMMENT '最近一次驳回原因',
    `submitted_at` DATETIME NULL COMMENT '最近一次提交审批时间',
    `reviewed_by` BIGINT NULL COMMENT '审批管理员ID',
    `reviewed_at` DATETIME NULL COMMENT '审批时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网站分享表';

-- 站点标签表（仅管理员维护，纯字典数据，物理删除）
CREATE TABLE IF NOT EXISTS `site_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(30) NOT NULL COMMENT '标签名',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序权重，越小越前',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点标签表';

-- 站点-标签关联表
CREATE TABLE IF NOT EXISTS `site_link_tag` (
    `site_id` BIGINT NOT NULL COMMENT '站点ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`site_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点-标签关联表';

-- 记事本表
CREATE TABLE IF NOT EXISTS `note` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '笔记ID',
    `user_id` BIGINT NOT NULL COMMENT '作者用户ID',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `content` LONGTEXT NULL COMMENT '正文HTML(白名单消毒后存储)',
    `summary` VARCHAR(300) NULL COMMENT '纯文本摘要，列表页展示用',
    `pinned` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶: 0否/1是',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0私有/1待审批/2已公开/3已驳回',
    `reject_reason` VARCHAR(500) NULL COMMENT '最近一次驳回原因',
    `submitted_at` DATETIME NULL COMMENT '最近一次提交审批时间',
    `reviewed_by` BIGINT NULL COMMENT '审批管理员ID',
    `reviewed_at` DATETIME NULL COMMENT '审批时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记事本表';

-- 笔记-插图关联表（正文里只存 image.id，这里记引用关系，便于清理与批量改可见性）
CREATE TABLE IF NOT EXISTS `note_image` (
    `note_id` BIGINT NOT NULL COMMENT '笔记ID',
    `image_id` BIGINT NOT NULL COMMENT '图片ID(image.id)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`note_id`, `image_id`),
    KEY `idx_image_id` (`image_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记-插图关联表';

-- 待办事项表（纯私有，无审批流）
CREATE TABLE IF NOT EXISTS `todo` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '待办ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `content` VARCHAR(500) NOT NULL COMMENT '待办内容',
    `done` TINYINT NOT NULL DEFAULT 0 COMMENT '0未完成/1已完成',
    `priority` TINYINT NOT NULL DEFAULT 1 COMMENT '优先级: 0低/1中/2高',
    `due_date` DATE NULL COMMENT '截止日期',
    `finished_at` DATETIME NULL COMMENT '完成时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_done` (`user_id`, `done`),
    KEY `idx_user_due` (`user_id`, `due_date`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待办事项表';

-- 消费类型表（用户自定义，纯私有）
CREATE TABLE IF NOT EXISTS `expense_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '类型ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '类型名称',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消费类型表';

-- 消费记录表（纯私有）
CREATE TABLE IF NOT EXISTS `expense_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消费记录ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `category_id` BIGINT NOT NULL COMMENT '消费类型ID(expense_category.id)',
    `amount` DECIMAL(12,2) NOT NULL COMMENT '消费金额',
    `remark` VARCHAR(500) NULL COMMENT '备注',
    `expense_date` DATE NOT NULL COMMENT '消费日期',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `expense_date`),
    KEY `idx_user_category` (`user_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消费记录表';

-- 生产项目配置表（绩效模块，纯私有）
CREATE TABLE IF NOT EXISTS `performance_project` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目ID',
    `user_id`       BIGINT NOT NULL COMMENT '所属用户ID',
    `name`          VARCHAR(100) NOT NULL COMMENT '项目名称',
    `work_quota`    DECIMAL(12,2) NOT NULL COMMENT '作业定额',
    `inspect_quota` DECIMAL(12,2) NOT NULL COMMENT '质检定额',
    `deleted`       TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产项目配置表';

-- 绩效记录表（纯私有）
CREATE TABLE IF NOT EXISTS `performance_record` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`          BIGINT NOT NULL COMMENT '所属用户ID',
    `project_id`       BIGINT NOT NULL COMMENT '项目ID(performance_project.id)',
    `work_date`        DATE NOT NULL COMMENT '工作日期',
    `process_type`     TINYINT NOT NULL COMMENT '工序类型: 0作业/1质检',
    `quota`            DECIMAL(12,2) NOT NULL COMMENT '定额效率(记录时快照)',
    `actual_workload`  DECIMAL(12,2) NOT NULL COMMENT '实际工作量',
    `performance_days` DECIMAL(12,5) NOT NULL COMMENT '绩效人天=实际工作量/定额效率',
    `deleted`          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `work_date`),
    KEY `idx_user_project` (`user_id`, `project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绩效记录表';

-- 加班记录表（纯私有）
CREATE TABLE IF NOT EXISTS `performance_overtime` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`        BIGINT NOT NULL COMMENT '所属用户ID',
    `work_date`      DATE NOT NULL COMMENT '加班日期',
    `overtime_hours` DECIMAL(4,1) NOT NULL COMMENT '加班时长(小时,0.5步进)',
    `overtime_days`  DECIMAL(12,4) NOT NULL COMMENT '加班天数=小时/8',
    `deleted`        TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加班记录表';

-- 迟到记录表（纯私有）
CREATE TABLE IF NOT EXISTS `performance_late` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`      BIGINT NOT NULL COMMENT '所属用户ID',
    `work_date`    DATE NOT NULL COMMENT '迟到日期',
    `late_minutes` INT NOT NULL COMMENT '迟到分钟(整数)',
    `late_days`    DECIMAL(12,5) NOT NULL COMMENT '迟到天数=分钟/480',
    `deleted`      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迟到记录表';

-- 工资配置表（纯私有，每用户一行）
CREATE TABLE IF NOT EXISTS `salary_config` (
    `id`                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `user_id`                BIGINT NOT NULL COMMENT '所属用户ID',
    `base_salary`            DECIMAL(12,5) NOT NULL DEFAULT 2000.00000 COMMENT '基本薪资',
    `post_performance`       DECIMAL(12,5) NOT NULL DEFAULT 500.00000 COMMENT '岗位绩效',
    `meal_allowance`         DECIMAL(12,5) NOT NULL DEFAULT 200.00000 COMMENT '餐补',
    `housing_allowance`      DECIMAL(12,5) NOT NULL DEFAULT 300.00000 COMMENT '房补',
    `full_attendance_bonus`  DECIMAL(12,5) NOT NULL DEFAULT 300.00000 COMMENT '全勤奖',
    `other_bonus`            DECIMAL(12,5) NOT NULL DEFAULT 100.00000 COMMENT '其他奖金',
    `pension`                DECIMAL(12,5) NOT NULL DEFAULT 360.32000 COMMENT '养老保险(扣除)',
    `medical_insurance`      DECIMAL(12,5) NOT NULL DEFAULT 90.08000 COMMENT '医疗保险(扣除)',
    `unemployment_insurance` DECIMAL(12,5) NOT NULL DEFAULT 13.51000 COMMENT '失业保险(扣除)',
    `deleted`                TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工资配置表';

-- 工资记录表（纯私有，每用户每月一条）
CREATE TABLE IF NOT EXISTS `salary_record` (
    `id`                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`                BIGINT NOT NULL COMMENT '所属用户ID',
    `month`                  VARCHAR(7) NOT NULL COMMENT '月份 YYYY-MM',
    `start_date`             DATE NOT NULL COMMENT '周期开始日期',
    `end_date`               DATE NOT NULL COMMENT '周期结束日期',
    `attendance_days`        DECIMAL(12,5) NOT NULL COMMENT '应出勤天数(可手动改)',
    `actual_attendance_days` DECIMAL(12,5) NOT NULL COMMENT '实际上班天数(绩效记录去重天数)',
    `performance_days`       DECIMAL(12,5) NOT NULL COMMENT '净绩效',
    `performance_salary`     DECIMAL(12,5) NOT NULL COMMENT '绩效薪资',
    `overtime_days`          DECIMAL(12,5) NOT NULL COMMENT '加班天数',
    `overtime_salary`        DECIMAL(12,5) NOT NULL COMMENT '加班工资',
    `late_minutes`           INT NOT NULL DEFAULT 0 COMMENT '迟到总分钟数',
    `attendance_ratio`       DECIMAL(12,5) NOT NULL COMMENT '出勤比',
    `base_salary`            DECIMAL(12,5) NOT NULL COMMENT '基本薪资(打折后)',
    `post_performance`       DECIMAL(12,5) NOT NULL COMMENT '岗位绩效(打折后)',
    `meal_allowance`         DECIMAL(12,5) NOT NULL COMMENT '餐补(打折后)',
    `housing_allowance`      DECIMAL(12,5) NOT NULL COMMENT '房补(打折后)',
    `full_attendance_bonus`  DECIMAL(12,5) NOT NULL COMMENT '全勤奖',
    `other_bonus`            DECIMAL(12,5) NOT NULL COMMENT '其他奖金',
    `pension`                DECIMAL(12,5) NOT NULL COMMENT '养老保险(扣除)',
    `medical_insurance`      DECIMAL(12,5) NOT NULL COMMENT '医疗保险(扣除)',
    `unemployment_insurance` DECIMAL(12,5) NOT NULL COMMENT '失业保险(扣除)',
    `total_salary`           DECIMAL(12,5) NOT NULL COMMENT '合计',
    `deleted`                TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_month` (`user_id`, `month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工资记录表';

-- 公告标签表（仅管理员维护，纯字典数据，物理删除）
CREATE TABLE IF NOT EXISTS `announcement_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(30) NOT NULL COMMENT '标签名',
    `color` VARCHAR(20) NOT NULL DEFAULT '#409EFF' COMMENT '标签颜色(HEX)',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序权重，越小越前',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告标签表';

-- 公告表（管理员发布，全站用户可见，逻辑删除）
CREATE TABLE IF NOT EXISTS `announcement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `title` VARCHAR(100) NOT NULL COMMENT '公告标题',
    `content` LONGTEXT NULL COMMENT '公告正文HTML(白名单消毒后存储)',
    `tag_id` BIGINT NULL COMMENT '标签ID(announcement_tag.id, 可空)',
    `created_by` BIGINT NOT NULL COMMENT '发布管理员ID(user.id)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tag` (`tag_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- 公告已读记录表（每用户每公告一条，无逻辑删除）
CREATE TABLE IF NOT EXISTS `announcement_read` (
    `announcement_id` BIGINT NOT NULL COMMENT '公告ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    PRIMARY KEY (`announcement_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告已读记录表';

-- 公告-插图关联表（正文里只存 image.id，这里记引用关系，便于删除时清理图片）
CREATE TABLE IF NOT EXISTS `announcement_image` (
    `announcement_id` BIGINT NOT NULL COMMENT '公告ID',
    `image_id` BIGINT NOT NULL COMMENT '图片ID(image.id)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`announcement_id`, `image_id`),
    KEY `idx_image_id` (`image_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告-插图关联表';

-- 话题圈表
CREATE TABLE IF NOT EXISTS `topic_circle` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '圈子ID',
    `name` VARCHAR(50) NOT NULL COMMENT '圈子名称',
    `description` VARCHAR(500) NULL COMMENT '圈子简介',
    `owner_id` BIGINT NOT NULL COMMENT '创建者用户ID',
    `post_count` INT NOT NULL DEFAULT 0 COMMENT '帖子数(冗余)',
    `member_count` INT NOT NULL DEFAULT 0 COMMENT '成员数(冗余)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_owner` (`owner_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题圈表';

-- 话题圈关注表
CREATE TABLE IF NOT EXISTS `topic_circle_member` (
    `circle_id` BIGINT NOT NULL COMMENT '圈子ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`circle_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题圈关注表';

-- 话题帖子表
CREATE TABLE IF NOT EXISTS `topic_post` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '帖子ID',
    `circle_id` BIGINT NOT NULL COMMENT '圈子ID',
    `user_id` BIGINT NOT NULL COMMENT '作者用户ID',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `content` LONGTEXT NULL COMMENT '正文HTML(白名单消毒后存储)',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数(冗余)',
    `comment_count` INT NOT NULL DEFAULT 0 COMMENT '评论数(冗余)',
    `favorite_count` INT NOT NULL DEFAULT 0 COMMENT '收藏数(冗余)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_circle` (`circle_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_created` (`created_at`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子表';

-- 话题帖子-图片关联表
CREATE TABLE IF NOT EXISTS `topic_post_image` (
    `post_id` BIGINT NOT NULL COMMENT '帖子ID',
    `image_id` BIGINT NOT NULL COMMENT '图片ID(image.id)',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序权重，越小越前',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`post_id`, `image_id`),
    KEY `idx_image_id` (`image_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子-图片关联表';

-- 话题帖子点赞表
CREATE TABLE IF NOT EXISTS `topic_post_like` (
    `post_id` BIGINT NOT NULL COMMENT '帖子ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`post_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子点赞表';

-- 话题帖子收藏表
CREATE TABLE IF NOT EXISTS `topic_post_favorite` (
    `post_id` BIGINT NOT NULL COMMENT '帖子ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`post_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子收藏表';

-- 话题帖子评论表
CREATE TABLE IF NOT EXISTS `topic_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `post_id` BIGINT NOT NULL COMMENT '帖子ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者用户ID',
    `content` VARCHAR(500) NOT NULL COMMENT '评论内容',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_post` (`post_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子评论表';

-- 火影忍者OL测验题库
CREATE TABLE IF NOT EXISTS `quiz_question` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `question` VARCHAR(500) NOT NULL COMMENT '问题',
    `normalized_question` VARCHAR(500) NULL COMMENT '归一化问题(去标点/空白/小写，用于查重)',
    `option_a` VARCHAR(200) NOT NULL COMMENT '选项A',
    `option_b` VARCHAR(200) NOT NULL COMMENT '选项B',
    `option_c` VARCHAR(200) NOT NULL COMMENT '选项C',
    `option_d` VARCHAR(200) NOT NULL COMMENT '选项D',
    `answer` VARCHAR(200) NOT NULL COMMENT '正确答案(选项文本)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0待审批/1已通过/2已驳回',
    `created_by` BIGINT NULL COMMENT '提交者用户ID(管理员添加为NULL)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常/1已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_normalized` (`normalized_question`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='火影忍者OL测验题库';

-- 火影忍者OL官方公告（抓取缓存，source_url 唯一键做 upsert）
CREATE TABLE IF NOT EXISTS `hyol_announcement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
    `source_url` VARCHAR(500) NOT NULL COMMENT '官网详情地址',
    `publish_time` VARCHAR(50) NULL COMMENT '发布时间(官网原文字符串)',
    `content` LONGTEXT NULL COMMENT '正文HTML(消毒后)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次抓取时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新/抓取时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_url` (`source_url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='火影忍者OL官方公告';

-- 火影忍者OL忍者图鉴（本地缓存，nid 唯一键做增量）
CREATE TABLE IF NOT EXISTS `hyol_ninja` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '忍者ID',
    `nid`          VARCHAR(32) NOT NULL COMMENT '官网忍者ID iNid',
    `name`         VARCHAR(128) NULL COMMENT '完整名称 szName',
    `nickname`     VARCHAR(64) NULL COMMENT '昵称 szNickname',
    `attr`         VARCHAR(32) NULL COMMENT '属性 szAttr',
    `star`         VARCHAR(8) NULL COMMENT '星级 iStar',
    `org`          VARCHAR(256) NULL COMMENT '阵营标签 szOrg',
    `pos`          VARCHAR(256) NULL COMMENT '定位 szPos',
    `get_way`      VARCHAR(256) NULL COMMENT '获得方式 szGetWay',
    `effect`       VARCHAR(256) NULL COMMENT '造成状态 szEffect',
    `effect_chase` VARCHAR(256) NULL COMMENT '追打状态 szEffectChase',
    `avatar_url`   VARCHAR(256) NULL COMMENT '本地头像路径',
    `avatar_url3`  VARCHAR(256) NULL COMMENT '本地高清立绘路径 szPicUrl3',
    `skill_oy`     VARCHAR(32) NULL COMMENT '奥义技能ID',
    `skill_pg`     VARCHAR(32) NULL COMMENT '普攻技能ID',
    `skill_bd1`    VARCHAR(32) NULL COMMENT '被动1技能ID',
    `skill_bd2`    VARCHAR(32) NULL COMMENT '被动2技能ID',
    `skill_bd3`    VARCHAR(32) NULL COMMENT '被动3技能ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次抓取时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新/抓取时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_nid` (`nid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='火影忍者OL忍者图鉴';

-- 火影忍者OL技能（本地缓存，skill_id 唯一键做增量）
CREATE TABLE IF NOT EXISTS `hyol_skill` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '技能ID',
    `skill_id`    VARCHAR(32) NOT NULL COMMENT '官网技能ID iSkillId',
    `title`       VARCHAR(128) NULL COMMENT '技能名 szTitle',
    `name`        VARCHAR(128) NULL COMMENT '所属忍者 szName',
    `type`        VARCHAR(32) NULL COMMENT '类型 szType',
    `moment`      TINYINT NULL COMMENT '是否瞬发 iMoment（1=瞬发）',
    `description` TEXT NULL COMMENT '技能描述 szDesc',
    `hurt_type`   VARCHAR(32) NULL COMMENT '伤害类型 szHurtType',
    `chase_status` VARCHAR(64) NULL COMMENT '追打条件 szChaseStatus',
    `hurt_status`  VARCHAR(128) NULL COMMENT '造成状态 szHurtStatus',
    `rare`         VARCHAR(16) NULL COMMENT '稀有度 szRare',
    `icon_url`    VARCHAR(256) NULL COMMENT '本地技能图标路径',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次抓取时间',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新/抓取时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_id` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='火影忍者OL技能';

-- 对已存在的 hyol_skill 表幂等补列（MySQL 8.0.19+ 支持；旧版自动跳过，continue-on-error=true）
ALTER TABLE `hyol_skill` ADD COLUMN IF NOT EXISTS `moment` TINYINT NULL COMMENT '是否瞬发 iMoment（1=瞬发）';
ALTER TABLE `hyol_skill` ADD COLUMN IF NOT EXISTS `chase_status` VARCHAR(64) NULL COMMENT '追打条件 szChaseStatus';
ALTER TABLE `hyol_skill` ADD COLUMN IF NOT EXISTS `hurt_status` VARCHAR(128) NULL COMMENT '造成状态 szHurtStatus';
ALTER TABLE `hyol_skill` ADD COLUMN IF NOT EXISTS `rare` VARCHAR(16) NULL COMMENT '稀有度 szRare';

-- 对已存在的 hyol_ninja 表幂等补列
ALTER TABLE `hyol_ninja` ADD COLUMN IF NOT EXISTS `avatar_url3` VARCHAR(256) NULL COMMENT '本地高清立绘路径 szPicUrl3';

-- 不插入任何账号记录。管理员通过注册普通账号后手动提权产生：
-- UPDATE `user` SET `role` = 'admin' WHERE `username` = '你的账号';
-- 改完需重新登录，role 写在 JWT 里，旧 token 仍是原角色。
