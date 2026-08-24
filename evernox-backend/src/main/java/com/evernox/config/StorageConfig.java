package com.evernox.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 存储配置 - 管理文件存储目录
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "evernox")
public class StorageConfig {

    /** 图片用途: 图床照片 */
    public static final int PURPOSE_PHOTO = 0;
    /** 图片用途: 相册封面 */
    public static final int PURPOSE_ALBUM_COVER = 1;
    /** 图片用途: 网站分享封面 */
    public static final int PURPOSE_SITE_COVER = 2;
    /** 图片用途: 记事本正文插图 */
    public static final int PURPOSE_NOTE_IMAGE = 3;
    /** 图片用途: 公告正文插图 */
    public static final int PURPOSE_ANNOUNCEMENT = 4;
    /** 图片用途: 话题帖子插图 */
    public static final int PURPOSE_TOPIC_IMAGE = 5;

    private static final String COVER_ALBUM_DIR = "covers/albums";
    private static final String COVER_SITE_DIR = "covers/sites";
    private static final String NOTE_IMAGE_DIR = "notes";
    private static final String ANNOUNCEMENT_DIR = "announcements";
    private static final String TOPIC_DIR = "topics";


    /**
     * 数据存储根目录
     */
    private String dataDir = "./data";

    /**
     * 缩略图配置
     */
    private ThumbnailConfig thumbnail = new ThumbnailConfig();

    @Data
    public static class ThumbnailConfig {
        private int maxWidth = 400;
        private double quality = 0.7;
    }

    @PostConstruct
    public void init() {
        try {
            Path basePath = Paths.get(dataDir).toAbsolutePath().normalize();
            Path imagesPath = basePath.resolve("images");
            Path thumbnailsPath = basePath.resolve("thumbnails");

            Files.createDirectories(imagesPath);
            Files.createDirectories(thumbnailsPath);
            // 封面与图床照片分开存放：封面不属于用户的图床内容
            Files.createDirectories(basePath.resolve(COVER_ALBUM_DIR));
            Files.createDirectories(basePath.resolve(COVER_SITE_DIR));
            // 笔记插图同理：只服务于记事本正文，不进「我的图床」
            Files.createDirectories(basePath.resolve(NOTE_IMAGE_DIR));
            // 公告插图同理：只服务于公告正文
            Files.createDirectories(basePath.resolve(ANNOUNCEMENT_DIR));
            // 话题帖子插图同理：只服务于话题帖子
            Files.createDirectories(basePath.resolve(TOPIC_DIR));

            log.info("数据存储目录已初始化: {}", basePath);
        } catch (IOException e) {
            log.error("初始化存储目录失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法初始化存储目录", e);
        }
    }

    /**
     * 获取数据存储根路径
     */
    public Path getDataPath() {
        return Paths.get(dataDir).toAbsolutePath().normalize();
    }

    /**
     * 获取图片存储路径
     */
    public Path getImagePath(Long userId) {
        return getDataPath().resolve("images").resolve(String.valueOf(userId));
    }

    /**
     * 获取封面存储路径
     *
     * 只接受 PURPOSE_ALBUM_COVER / PURPOSE_SITE_COVER；purpose 由调用方在入口处
     * 规范化过，这里传进别的值属于代码错误，直接抛出而不是悄悄写进 images 目录。
     */
    public Path getCoverPath(Long userId, int purpose) {
        String subDir = switch (purpose) {
            case PURPOSE_ALBUM_COVER -> COVER_ALBUM_DIR;
            case PURPOSE_SITE_COVER -> COVER_SITE_DIR;
            default -> throw new IllegalArgumentException("非封面用途: " + purpose);
        };
        return getDataPath().resolve(subDir).resolve(String.valueOf(userId));
    }

    /**
     * 获取笔记插图存储路径
     */
    public Path getNoteImagePath(Long userId) {
        return getDataPath().resolve(NOTE_IMAGE_DIR).resolve(String.valueOf(userId));
    }

    /**
     * 获取公告插图存储路径
     */
    public Path getAnnouncementImagePath(Long userId) {
        return getDataPath().resolve(ANNOUNCEMENT_DIR).resolve(String.valueOf(userId));
    }

    /**
     * 获取话题帖子插图存储路径
     */
    public Path getTopicImagePath(Long userId) {
        return getDataPath().resolve(TOPIC_DIR).resolve(String.valueOf(userId));
    }

    /**
     * 获取缩略图存储路径
     */
    public Path getThumbnailPath(Long userId) {
        return getDataPath().resolve("thumbnails").resolve(String.valueOf(userId));
    }

    /**
     * 确保用户目录存在
     */
    public void ensureUserDirs(Long userId) throws IOException {
        Files.createDirectories(getImagePath(userId));
        Files.createDirectories(getThumbnailPath(userId));
    }
}
