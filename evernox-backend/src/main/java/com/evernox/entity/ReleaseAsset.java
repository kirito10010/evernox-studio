package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 版本附件。
 *
 * releaseId 为 NULL 表示已上传、尚未绑定到版本（暂存态），
 * 公开接口只查询已发布版本下的附件，暂存附件不会外泄。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("release_asset")
public class ReleaseAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long releaseId;

    /** 分片上传会话ID */
    private String uploadId;

    private String fileName;

    /** 文件字节数 */
    private Long size;

    /** Base64(SHA-512(bytes)) */
    private String sha512;

    /** 相对 data 目录的存储路径 */
    private String storagePath;

    private Long downloadCount;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
