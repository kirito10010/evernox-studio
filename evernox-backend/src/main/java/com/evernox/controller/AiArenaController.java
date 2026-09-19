package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.common.ResultCode;
import com.evernox.dto.ArenaCategoryOption;
import com.evernox.dto.ArenaFilterOptions;
import com.evernox.dto.ArenaRankItem;
import com.evernox.dto.ArenaSyncStatus;
import com.evernox.exception.BusinessException;
import com.evernox.service.AiArenaCrawler;
import com.evernox.service.AiArenaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Code Arena 模型排行榜接口（查询对登录用户开放，同步/导入仅管理员）
 */
@RestController
@RequestMapping("/ai-arena")
@RequiredArgsConstructor
public class AiArenaController {

    /** 数据包内单个页面 HTML 的上限，防止解压炸弹（正常榜单页约 1.4MB） */
    private static final int MAX_HTML_BYTES = 8 * 1024 * 1024;

    private final AiArenaService aiArenaService;

    @GetMapping("/categories")
    public Result<List<ArenaCategoryOption>> categories() {
        return Result.success(aiArenaService.listCategories());
    }

    @GetMapping("/items")
    public Result<List<ArenaRankItem>> items(
            @RequestParam String category,
            @RequestParam(required = false) String org,
            @RequestParam(required = false) String priceType,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String keyword) {
        return Result.success(aiArenaService.listItems(category, org, priceType, minPrice, maxPrice, keyword));
    }

    @GetMapping("/filters")
    public Result<ArenaFilterOptions> filters(@RequestParam String category) {
        return Result.success(aiArenaService.getFilterOptions(category));
    }

    @GetMapping("/sync/status")
    public Result<ArenaSyncStatus> syncStatus() {
        return Result.success(aiArenaService.getSyncStatus());
    }

    /** 服务器直连官网抓取（官网 CDN 拦截机房 IP 时会返回 403，此时改用 /import） */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> sync() {
        aiArenaService.triggerSyncAsync();
        return Result.<Void>success("已开始同步", null);
    }

    /**
     * 导入「本地抓取 → 打包上传」的榜单数据包（zip，条目名形如 overall.html）。
     * 用于服务器 IP 被官网风控拦截（403）的场景：本地脚本负责下载页面，
     * 服务端负责解析入库，解析规则只有 AiArenaCrawler 一份。
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('admin')")
    public Result<ArenaSyncStatus> importPackage(@RequestParam("file") MultipartFile file) {
        ArenaSyncStatus status = aiArenaService.importHtml(readHtmlPackage(file));
        return Result.success(status.isLastSuccess() ? "导入完成" : "导入未成功", status);
    }

    /** 读取 zip 数据包，返回 分类slug → 页面 HTML；只识别已知分类的文件名 */
    private Map<String, String> readHtmlPackage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要导入的数据包");
        }
        Map<String, String> result = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = baseName(entry.getName());
                AiArenaCrawler.Category category = AiArenaCrawler.categoryBySlug(stripExtension(fileName));
                if (category == null) {
                    continue;
                }
                byte[] bytes = zis.readNBytes(MAX_HTML_BYTES + 1);
                if (bytes.length > MAX_HTML_BYTES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "数据包内单个页面过大：" + fileName);
                }
                result.put(category.slug(), new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "数据包读取失败：" + e.getMessage());
        }
        if (result.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "数据包里没有可识别的分类页面（文件名应为 overall.html、frontend.html 等，请用 fetch-arena-leaderboard 脚本生成）");
        }
        return result;
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
