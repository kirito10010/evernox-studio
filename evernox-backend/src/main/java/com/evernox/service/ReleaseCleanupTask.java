package com.evernox.service;

import com.evernox.config.ReleaseProperties;
import com.evernox.entity.ReleaseAsset;
import com.evernox.repository.ReleaseAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 清理超期的分片上传目录与未绑定附件。
 *
 * 未绑定附件只可能来自“上传完成但用户没保存版本”，
 * 已绑定附件由版本编辑/删除负责回收，任务不会碰它们。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseCleanupTask {

    private final ReleaseProperties releaseProperties;
    private final ReleaseAssetRepository assetRepository;
    private final ReleaseStorageService storageService;

    @Scheduled(cron = "0 40 4 * * *")
    public void cleanup() {
        LocalDateTime before = LocalDateTime.now()
                .minusHours(Math.max(releaseProperties.getRetentionHours(), 1));

        storageService.cleanupUploadDirs(before);

        List<ReleaseAsset> staged = assetRepository.findStagedBefore(before);
        for (ReleaseAsset asset : staged) {
            assetRepository.deleteById(asset.getId());
            storageService.deleteQuietly(asset.getStoragePath());
        }
        if (!staged.isEmpty()) {
            log.info("清理未绑定版本附件: {} 个", staged.size());
        }
    }
}
