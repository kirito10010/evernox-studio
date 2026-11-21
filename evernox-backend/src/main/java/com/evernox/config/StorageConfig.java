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
    private static final String MIAO_IMAGE_DIR = "miao/images";
    private static final String MALL_IMAGE_DIR = "hyol-mall/images";
    private static final String FLAG_IMAGE_DIR = "hyol-flag/images";
    private static final String BATTLE_IMAGE_DIR = "hyol-battle/images";
    private static final String CODEX_DIR = "hyol-codex";
    private static final String CODEX_PORTRAIT_DIR = "hyol-codex/images/portraits";
    private static final String CODEX_HEAD_DIR = "hyol-codex/images/heads";
    private static final String RELEASE_DIR = "releases";
    private static final String RELEASE_UPLOAD_DIR = "releases/.uploads";

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
            // 喵问答：看图题的截图，按内容哈希命名
            Files.createDirectories(basePath.resolve(MIAO_IMAGE_DIR));
            // 商城特惠：商品图标，按物品ID命名
            Files.createDirectories(basePath.resolve(MALL_IMAGE_DIR));
            // 彩旗献礼：商品图标，与商城特惠分开存，避免互相覆盖
            Files.createDirectories(basePath.resolve(FLAG_IMAGE_DIR));
            // 战场的召唤：商品与技能图标
            Files.createDirectories(basePath.resolve(BATTLE_IMAGE_DIR));
            // 忍者图鉴：抓包导入后的数据集与立绘
            Files.createDirectories(basePath.resolve(CODEX_DIR));
            Files.createDirectories(basePath.resolve(CODEX_PORTRAIT_DIR));
            Files.createDirectories(basePath.resolve(CODEX_HEAD_DIR));
            // 版本发布：正式附件与分片临时目录分开
            Files.createDirectories(basePath.resolve(RELEASE_DIR));
            Files.createDirectories(basePath.resolve(RELEASE_UPLOAD_DIR));


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

    /** 版本发布附件根目录 */
    public Path getReleaseRoot() {
        return getDataPath().resolve(RELEASE_DIR);
    }

    /** 喵问答题图目录 */
    public Path getMiaoImagePath() {
        return getDataPath().resolve(MIAO_IMAGE_DIR);
    }

    /** 商城特惠商品图标目录（文件名即物品ID.png） */
    public Path getMallImagePath() {
        return getDataPath().resolve(MALL_IMAGE_DIR);
    }

    /** 彩旗献礼商品图标目录 */
    public Path getFlagImagePath() {
        return getDataPath().resolve(FLAG_IMAGE_DIR);
    }

    /** 战场的召唤商品/技能图标目录 */
    public Path getBattleImagePath() {
        return getDataPath().resolve(BATTLE_IMAGE_DIR);
    }

    /** 忍者图鉴数据目录（ninjas.json / meta.json / images） */
    public Path getCodexPath() {
        return getDataPath().resolve(CODEX_DIR);
    }

    /** 忍者图鉴立绘目录 */
    public Path getCodexPortraitPath() {
        return getDataPath().resolve(CODEX_PORTRAIT_DIR);
    }

    /** 忍者图鉴头像目录 */
    public Path getCodexHeadPath() {
        return getDataPath().resolve(CODEX_HEAD_DIR);
    }

    /** 分片上传临时根目录 */
    public Path getReleaseUploadRoot() {
        return getDataPath().resolve(RELEASE_UPLOAD_DIR);
    }

    /** 单次分片上传的临时目录 */
    public Path getReleaseUploadDir(String uploadId) {
        return getReleaseUploadRoot().resolve(uploadId);
    }

    /** 正式附件目录：projects/{projectId}/{storageKey} */
    public Path getReleaseAssetDir(Long projectId, String storageKey) {
        return getReleaseRoot().resolve(String.valueOf(projectId)).resolve(storageKey);
    }


    /**
     * 确保用户目录存在
     */
    public void ensureUserDirs(Long userId) throws IOException {
        Files.createDirectories(getImagePath(userId));
        Files.createDirectories(getThumbnailPath(userId));
    }
}
