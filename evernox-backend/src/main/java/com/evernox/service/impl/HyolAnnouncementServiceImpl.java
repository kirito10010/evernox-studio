package com.evernox.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolAnnouncementResponse;
import com.evernox.dto.HyolRefreshResponse;
import com.evernox.entity.HyolAnnouncement;
import com.evernox.exception.BusinessException;
import com.evernox.repository.HyolAnnouncementRepository;
import com.evernox.service.HyolAnnouncementService;
import com.evernox.util.ImageTypeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * 火影忍者OL官方公告服务实现
 *
 * 官网为 GBK 编码静态页，抓取后按 GBK 解码再解析。
 * 正文图片下载到本地（data/hyol-announcement/），下次访问不再请求官网 CDN。
 * 增量抓取：source_url 已缓存则跳过，只抓新增公告。
 * 详情抓取并发执行（限 5 线程），避免首次抓取串行过慢导致超时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class HyolAnnouncementServiceImpl implements HyolAnnouncementService {

    private static final String BASE_URL = "https://huoying.qq.com";
    private static final String LIST_URL =
            "https://huoying.qq.com/webplat/info/news_version3/5491/5492/5494/m3987/list_1.shtml";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Charset GBK = Charset.forName("GBK");
    private static final String IMAGE_DIR = "hyol-announcement";
    /** 感知无损压缩的 JPEG 质量 */
    private static final float JPEG_QUALITY = 0.9f;
    /** 可解码处理的最大像素数，超过则跳过压缩避免 OOM */
    private static final int MAX_PROCESS_PIXELS = 30_000_000;

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** 正文白名单：只保留结构与图片/链接，去掉脚本与样式 */
    private static final Safelist CONTENT_SAFELIST = new Safelist()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "s",
                    "h1", "h2", "h3", "h4", "ul", "ol", "li", "span", "div",
                    "img", "a", "table", "tbody", "tr", "td", "th", "thead")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("a", "href", "title")
            .addAttributes("td", "width")
            .addAttributes("th", "width")
            .addAttributes("table", "width", "border")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https");

    /** 详情抓取线程池（5 线程并发，避免串行过慢；守护线程不阻塞退出） */
    private static final ExecutorService FETCH_POOL = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "hyol-fetch");
        t.setDaemon(true);
        return t;
    });

    private final HyolAnnouncementRepository announcementRepository;
    private final StorageConfig storageConfig;

    @Override
    public HyolRefreshResponse refresh() {
        String listHtml = fetchGbk(LIST_URL);
        Document doc = Jsoup.parse(listHtml);

        List<FetchTask> tasks = new ArrayList<>();
        for (Element li : doc.select("ul.list_con li")) {
            Element titleLink = li.selectFirst("a.n-title");
            if (titleLink == null) {
                continue;
            }
            String title = titleLink.text().trim();
            String href = titleLink.attr("href");
            Element dateEl = li.selectFirst("span.date");
            String listDate = dateEl != null ? dateEl.text().trim() : "";
            if (title.isEmpty() || href.isEmpty()) {
                continue;
            }
            String detailUrl = absolutize(href);
            // 增量：已缓存的不重复抓取
            if (exists(detailUrl)) {
                continue;
            }
            tasks.add(new FetchTask(title, detailUrl, listDate));
        }

        AtomicInteger fetched = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (FetchTask task : tasks) {
            futures.add(FETCH_POOL.submit(() -> {
                try {
                    fetchAndStore(task);
                    fetched.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.warn("抓取公告详情失败: url={}, err={}", task.detailUrl(), e.getMessage());
                }
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.warn("等待抓取任务失败: {}", e.getMessage());
            }
        }

        log.info("官方公告刷新完成: fetched={}, failed={}", fetched.get(), failed.get());
        return HyolRefreshResponse.builder().fetched(fetched.get()).failed(failed.get()).build();
    }

    @Override
    public IPage<HyolAnnouncementResponse> list(int page, int size) {
        if (announcementRepository.selectCount(new LambdaQueryWrapper<>()) == 0) {
            try {
                refresh();
            } catch (Exception e) {
                log.warn("首次自动抓取官方公告失败: {}", e.getMessage());
            }
        }
        IPage<HyolAnnouncement> raw = announcementRepository.selectPage(newPage(page, size),
                new LambdaQueryWrapper<HyolAnnouncement>()
                        .orderByDesc(HyolAnnouncement::getPublishTime)
                        .orderByDesc(HyolAnnouncement::getId));
        return raw.convert(HyolAnnouncementResponse::brief);
    }

    @Override
    public HyolAnnouncementResponse getDetail(Long id) {
        HyolAnnouncement a = announcementRepository.selectById(id);
        if (a == null) {
            throw new BusinessException("公告不存在");
        }
        return HyolAnnouncementResponse.detail(a);
    }

    // ==================== 定时抓取（固定时间表） ====================

    /** 每周三 17:06：停机更新公告周三傍晚发布 */
    @Scheduled(cron = "0 6 17 ? * WED")
    public void scheduledWednesday() {
        safeRefresh();
    }

    /** 每周四 20:06 */
    @Scheduled(cron = "0 6 20 ? * THU")
    public void scheduledThursday2006() {
        safeRefresh();
    }

    /** 每周四 22:06 */
    @Scheduled(cron = "0 6 22 ? * THU")
    public void scheduledThursday2206() {
        safeRefresh();
    }

    /** 每天 09:06 兜底：覆盖「新区开启」「联服」等不定期公告 */
    @Scheduled(cron = "0 6 9 * * *")
    public void scheduledDaily() {
        safeRefresh();
    }

    private void safeRefresh() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("自动刷新官方公告失败: {}", e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /** 单条公告：抓详情 → 消毒 → 下载图片 → 入库 */
    private void fetchAndStore(FetchTask task) {
        Document detail = Jsoup.parse(fetchGbk(task.detailUrl()));
        String title = textOf(detail.selectFirst("h2.detail-title"), task.title());
        String publishTime = textOf(detail.selectFirst("p.detail-info span"), task.listDate());
        Element contentEl = detail.selectFirst("#detail_con");
        String content = contentEl != null ? sanitize(contentEl.html()) : "";
        content = downloadAndRewriteImages(content);
        insert(title, task.detailUrl(), publishTime, content);
    }

    private record FetchTask(String title, String detailUrl, String listDate) {
    }

    private boolean exists(String sourceUrl) {
        Long count = announcementRepository.selectCount(new LambdaQueryWrapper<HyolAnnouncement>()
                .eq(HyolAnnouncement::getSourceUrl, sourceUrl));
        return count != null && count > 0;
    }

    private void insert(String title, String sourceUrl, String publishTime, String content) {
        announcementRepository.insert(HyolAnnouncement.builder()
                .title(title)
                .sourceUrl(sourceUrl)
                .publishTime(publishTime)
                .content(content)
                .build());
    }

    private String fetchGbk(String url) {
        byte[] bytes = HttpRequest.get(url)
                .header("User-Agent", UA)
                .timeout(15000)
                .execute()
                .bodyBytes();
        return new String(bytes, GBK);
    }

    /** 相对地址转绝对 https 地址 */
    private String absolutize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("/")) {
            return BASE_URL + url;
        }
        return url;
    }

    /** 消毒正文：相对地址转绝对 + 白名单过滤（去掉脚本/事件/样式） */
    private String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element img : doc.select("img")) {
            img.attr("src", absolutize(img.attr("src")));
        }
        for (Element a : doc.select("a")) {
            a.attr("href", absolutize(a.attr("href")));
        }
        return Jsoup.clean(doc.body().html(), CONTENT_SAFELIST);
    }

    /** 下载正文图片到本地并改写 src；下载失败保留原外链 */
    private String downloadAndRewriteImages(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src");
            if (!src.startsWith("http")) {
                continue;
            }
            try {
                String local = downloadImage(src);
                if (local != null) {
                    img.attr("src", local);
                }
            } catch (Exception e) {
                log.warn("公告图片下载失败: url={}, err={}", src, e.getMessage());
            }
        }
        return doc.body().html();
    }

    private String downloadImage(String url) throws IOException {
        String name = SecureUtil.md5(url) + extOf(url);
        Path file = storageConfig.getDataPath().resolve(IMAGE_DIR).resolve(name);
        // 已下载过则直接复用（同名图片只下一次）
        if (Files.exists(file)) {
            return "/api/hyol/announcement/image/" + name;
        }
        byte[] bytes = HttpRequest.get(url)
                .header("User-Agent", UA)
                .timeout(20000)
                .execute()
                .bodyBytes();
        if (bytes.length == 0) {
            return null;
        }
        byte[] stored = compressImage(bytes);
        Files.createDirectories(file.getParent());
        Files.write(file, stored);
        return "/api/hyol/announcement/image/" + name;
    }

    /** 感知无损压缩：非 GIF、非透明的大图重编码为 JPEG，仅当更小时才采用 */
    private byte[] compressImage(byte[] raw) {
        try {
            String mime = ImageTypeValidator.detectMimeType(raw);
            if ("image/gif".equals(mime)) {
                return raw;
            }
            int[] dims = readImageDimensions(raw);
            if (dims == null || (long) dims[0] * dims[1] > MAX_PROCESS_PIXELS) {
                return raw;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null || img.getColorModel().hasAlpha()) {
                return raw;
            }
            byte[] compressed = encodeJpeg(img, JPEG_QUALITY);
            if (compressed != null && compressed.length > 0 && compressed.length < raw.length) {
                return compressed;
            }
            return raw;
        } catch (Exception e) {
            log.warn("公告图片压缩失败，保留原图: {}", e.getMessage());
            return raw;
        }
    }

    /** 仅读取图片宽高（不解码），无法识别时返回 null */
    private int[] readImageDimensions(byte[] raw) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0) {
                    return null;
                }
                return new int[]{w, h};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private String extOf(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot > path.lastIndexOf('/')) {
            String ext = path.substring(dot).toLowerCase();
            if (ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp)")) {
                return ext;
            }
        }
        return ".jpg";
    }

    private String textOf(Element el, String fallback) {
        return el != null ? el.text().trim() : fallback;
    }

    private Page<HyolAnnouncement> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
