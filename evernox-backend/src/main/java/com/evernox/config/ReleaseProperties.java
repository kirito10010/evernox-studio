package com.evernox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 版本发布中心配置。
 *
 * 分片大小需小于等于 spring.servlet.multipart.max-file-size，
 * 同时也要小于等于 Nginx 的 client_max_body_size。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "evernox.release")
public class ReleaseProperties {

    /** 单个附件上限：2GB */
    private long maxFileSize = 2L * 1024 * 1024 * 1024;

    /** 分片大小：4MB */
    private long chunkSize = 4L * 1024 * 1024;

    /** 单片上限：8MB（与 multipart 上限一致） */
    private long maxChunkSize = 8L * 1024 * 1024;

    /** 未完成上传 / 未绑定附件的保留时长（小时） */
    private int retentionHours = 24;
}
