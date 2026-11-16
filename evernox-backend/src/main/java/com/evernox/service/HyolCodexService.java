package com.evernox.service;

import com.evernox.dto.HyolCodexImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 忍者图鉴 · 抓包导入与数据集读取
 */
public interface HyolCodexService {

    /** 数据集原文；尚未导入时返回 null。 */
    byte[] dataset();

    /** 数据集最后修改时间（毫秒），用于 ETag。 */
    long datasetLastModified();

    /** 导入元信息；尚未导入时返回 null。 */
    Map<String, Object> meta();

    /** 导入抓包文件夹（只处理 http-*.jsonl）。 */
    HyolCodexImportResponse importFolder(List<MultipartFile> files);

    /** 清空数据集，前端回退到内置静态数据。 */
    void deleteDataset();

    /** 按文件名查找已落盘的立绘 / 头像；不存在返回 null。 */
    Path resolveImage(String filename);
}
