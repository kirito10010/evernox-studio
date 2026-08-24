package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储空间统计响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageStatsResponse {

    /** 数据目录所在磁盘总容量（字节） */
    private long diskTotal;

    /** 数据目录所在磁盘可用容量（字节） */
    private long diskFree;

    /** 数据目录所在磁盘已用容量（字节） */
    private long diskUsed;

    /** 当前用户上传照片占用（字节，含其相册封面） */
    private long imagesUsed;

    /** 全平台所有用户照片占用（字节），用于计算占用率的分母 */
    private long allImagesUsed;

    /** 当前用户照片数量 */
    private long imagesCount;
}
