package com.evernox.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 【重要·必读】数据库结构自动迁移 —— 给已存在的表新增列时必须在这里登记！
 *
 * 约定（跨文件规则，改动 schema.sql 时务必遵守）：
 *   schema.sql 用 CREATE TABLE IF NOT EXISTS，只对「新建的表」生效，
 *   对「已存在的旧表」不会自动加列。
 *   因此：任何人在 schema.sql 里给「已存在的表」新增列时，
 *   必须同时在本类的 run() 里加一行 addColumnIfMissing(...)，
 *   否则老库升级后查询会报「字段不存在」。
 *
 * 用法（在 run() 里追加一行）：
 *   addColumnIfMissing("表名", "列名", "列定义(类型/默认值/注释)");
 *   addIndexIfMissing("表名", "索引名", "`列1`, `列2`");
 *
 * 本类是幂等的：先查 information_schema，列/索引已存在则跳过，不会重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // ===== 新增列请在这里登记（老库启动时自动补齐） =====
        addColumnIfMissing("site_link", "weight",
                "INT NOT NULL DEFAULT 0 COMMENT '排序权重，越大越靠前'");

        // 沙盘：last_grant_date → last_grant_at（资源产出改为按小时发放）
        renameColumnIfNeeded("game_player", "last_grant_date", "last_grant_at",
                "DATETIME NULL COMMENT '上次小时发放时间'");

        // 应援板：像素锁定
        addColumnIfMissing("support_pixel", "locked",
                "TINYINT NOT NULL DEFAULT 0 COMMENT '是否锁定 0/1'");

        // AI 资讯：英文标题/摘要的中文翻译
        addColumnIfMissing("ai_news_item", "title_zh",
                "VARCHAR(512) NULL COMMENT '中文标题(自动翻译)'");
        addColumnIfMissing("ai_news_item", "summary_zh",
                "TEXT NULL COMMENT '中文摘要(自动翻译)'");

        // 组织周记录：5+ 处查询都是 organization_id + week_date 组合过滤，
        // 原表只有 idx_week_date(week_date)，缺 organization_id 打头的索引
        addIndexIfMissing("org_week_record", "idx_org_week", "`organization_id`, `week_date`");
        // ===== 上面是登记区 =====
    }

    private void renameColumnIfNeeded(String table, String oldColumn, String newColumn, String definition) {
        try {
            Integer oldCount = columnCount(table, oldColumn);
            Integer newCount = columnCount(table, newColumn);
            if (oldCount != null && oldCount > 0 && (newCount == null || newCount == 0)) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` CHANGE COLUMN `" + oldColumn + "` `" + newColumn + "` " + definition);
                log.info("自动迁移完成：表 {} 列 {} 重命名为 {}", table, oldColumn, newColumn);
            }
        } catch (Exception e) {
            log.error("自动迁移失败：表 {} 列 {} 重命名失败：{}", table, oldColumn, e.getMessage());
        }
    }

    private Integer columnCount(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
    }

    /** 索引是否已存在。按索引名判重（同名索引在同一张表里只能有一个） */
    private Integer indexCount(String table, String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, table, indexName);
    }

    /**
     * 幂等加索引：先查 information_schema.STATISTICS，已存在则跳过。
     *
     * MySQL 8 对二级索引的 ADD INDEX 是 INPLACE 在线 DDL，不会锁表。
     * 失败只记日志、不阻断启动，并打印出可手工执行的 ALTER 语句。
     */
    private void addIndexIfMissing(String table, String indexName, String columns) {
        try {
            Integer count = indexCount(table, indexName);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD INDEX `" + indexName + "` (" + columns + ")");
            log.info("自动迁移完成：表 {} 新增索引 {} ({})", table, indexName, columns);
        } catch (Exception e) {
            log.error("自动迁移失败：表 {} 新增索引 {} 失败，请手动执行 ALTER：{}",
                    table, indexName, e.getMessage());
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            Integer count = columnCount(table, column);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
            log.info("自动迁移完成：表 {} 新增列 {}", table, column);
        } catch (Exception e) {
            log.error("自动迁移失败：表 {} 新增列 {} 失败，请手动执行 ALTER：{}",
                    table, column, e.getMessage());
        }
    }
}
