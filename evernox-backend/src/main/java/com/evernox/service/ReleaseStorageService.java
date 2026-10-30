package com.evernox.service;

import com.evernox.dto.UploadInitResponse;
import com.evernox.entity.ReleaseAsset;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 版本附件磁盘存储服务：分片、合并、哈希、路径安全与清理。
 */
public interface ReleaseStorageService {

    /** 校验并清洗文件名；不合法时抛出 BusinessException */
    String sanitizeFileName(String rawFileName);

    /** 把相对 storagePath 解析为绝对路径，并校验仍位于 data/releases 下 */
    Path resolve(String storagePath);

    /** 合并分片、计算 Base64(SHA-512)，落盘并写入未绑定附件 */
    ReleaseAsset completeUpload(String uploadId, Long ownerId);

    /** 初始化分片会话，写入 meta.json */
    UploadInitResponse initUpload(Long projectId, String fileName, long fileSize, Long ownerId);

    /** 保存单个分片，返回已收到的分片数量 */
    int saveChunk(String uploadId, int index, MultipartFile chunk, Long ownerId);


    /** 取消上传，删除临时目录 */
    void cancelUpload(String uploadId, Long ownerId);

    /** 删除尚未绑定版本的附件（含文件） */
    void deleteStagedAsset(Long assetId, Long ownerId);


    /** 删除附件文件；失败只记日志 */
    void deleteQuietly(String storagePath);

    /** 清理超期的分片临时目录 */
    void cleanupUploadDirs(LocalDateTime before);
}

