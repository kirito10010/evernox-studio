package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员视角的全平台图片/相册统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAssetStatsResponse {

    /** 图片总数 */
    private long totalImages;

    /** 其中私密图片数 */
    private long privateImages;

    /** 相册总数 */
    private long totalAlbums;

    /** 其中私密相册数 */
    private long privateAlbums;

    /** 图片占用字节数 */
    private long imagesUsedBytes;
}
