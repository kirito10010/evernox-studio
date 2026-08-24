package com.evernox.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolNinjaResponse;
import com.evernox.dto.HyolRefreshResponse;
import com.evernox.entity.HyolNinja;
import com.evernox.entity.HyolSkill;
import com.evernox.repository.HyolNinjaRepository;
import com.evernox.repository.HyolSkillRepository;
import com.evernox.service.HyolNinjaService;
import com.evernox.util.ImageTypeValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 火影忍者OL忍者图鉴服务实现
 *
 * 数据源为官网 JSON 接口（POST + Referer/Origin/UA/X-Requested-With 头，否则 Access Denied）。
 * 头像/技能图标下载到本地（data/hyol-ninja/），能压缩就压缩；下载失败降级保留 CDN 外链。
 * 增量：nid（忍者）/ skill_id（技能）已存在则跳过，重复刷新不重复下载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class HyolNinjaServiceImpl implements HyolNinjaService {

    private static final String API_URL = "https://bang.qq.com/ugc1/getHuoyingData";
    private static final String REFERER = "https://bang.qq.com/tool/huoying/mnq.htm";
    private static final String ORIGIN = "https://bang.qq.com";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final String IMAGE_DIR = "hyol-ninja";
    private static final String DEFAULT_AVATAR =
            "https://ossweb-img.qq.com/images/bangbang/home/tool/huoying/avatar.png";
    private static final String DEFAULT_SKILL_ICON =
            "https://ossweb-img.qq.com/images/bangbang/home/tool/huoying/tf.jpg";

    /** 感知无损压缩的 JPEG 质量 */
    private static final float JPEG_QUALITY = 0.9f;
    /** 可解码处理的最大像素数，超过则跳过压缩避免 OOM */
    private static final int MAX_PROCESS_PIXELS = 30_000_000;
    private static final int MAX_PAGE_SIZE = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 图片下载线程池（10 线程并发，避免首次抓取串行过慢） */
    private static final ExecutorService IMAGE_POOL = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r, "hyol-ninja-img");
        t.setDaemon(true);
        return t;
    });

    private final HyolNinjaRepository ninjaRepository;
    private final HyolSkillRepository skillRepository;
    private final StorageConfig storageConfig;

    @Override
    public HyolRefreshResponse refresh() {
        JsonNode root = fetchJson();
        JsonNode ninjaNodes = root.path("data").path("ninjas");
        JsonNode skillNodes = root.path("data").path("skills");

        // 技能按 iSkillId 建索引（忍者技能槽引用 iSkillId）
        Map<String, SkillData> skillBySkillId = new ConcurrentHashMap<>();
        if (skillNodes.isArray()) {
            for (JsonNode s : skillNodes) {
                String sid = text(s, "iSkillId");
                if (sid != null && !sid.isBlank()) {
                    skillBySkillId.put(sid, new SkillData(
                            sid, text(s, "szTitle"), text(s, "szName"), text(s, "szType"),
                            text(s, "iMoment"), text(s, "szDesc"), text(s, "szHurtType"),
                            text(s, "szChaseStatus"), text(s, "szHurtStatus"), text(s, "szRare"), text(s, "szPicUrl")));
                }
            }
        }

        List<NinjaData> ninjas = new ArrayList<>();
        if (ninjaNodes.isArray()) {
            for (JsonNode n : ninjaNodes) {
                ninjas.add(new NinjaData(
                        text(n, "iNid"), text(n, "szName"), text(n, "szNickname"),
                        text(n, "szAttr"), text(n, "iStar"), text(n, "szOrg"),
                        text(n, "szPos"), text(n, "szGetWay"), text(n, "szEffect"),
                        text(n, "szEffectChase"), text(n, "szPicUrl2"), text(n, "szPicUrl3"),
                        text(n, "iOySkill"), text(n, "iPgSkill"), text(n, "iBdSkill1"),
                        text(n, "iBdSkill2"), text(n, "iBdSkill3")));
            }
        }

        // 增量：预载已存在的 nid / skill_id，避免逐条查询
        Set<String> existingNids = ninjaRepository.selectList(
                        new LambdaQueryWrapper<HyolNinja>().select(HyolNinja::getNid))
                .stream().map(HyolNinja::getNid).collect(Collectors.toSet());
        Set<String> existingSkillIds = skillRepository.selectList(
                        new LambdaQueryWrapper<HyolSkill>().select(HyolSkill::getSkillId))
                .stream().map(HyolSkill::getSkillId).collect(Collectors.toSet());

        List<NinjaData> newNinjas = ninjas.stream()
                .filter(n -> n.nid() != null && !n.nid().isBlank() && !existingNids.contains(n.nid()))
                .toList();

        // 收集新忍者引用的、尚未入库的技能
        Set<String> referencedSkillIds = new HashSet<>();
        for (NinjaData n : newNinjas) {
            collectSkill(referencedSkillIds, n.skillOy());
            collectSkill(referencedSkillIds, n.skillPg());
            collectSkill(referencedSkillIds, n.skillBd1());
            collectSkill(referencedSkillIds, n.skillBd2());
            collectSkill(referencedSkillIds, n.skillBd3());
        }
        List<SkillData> newSkills = referencedSkillIds.stream()
                .filter(id -> !existingSkillIds.contains(id))
                .map(skillBySkillId::get)
                .filter(s -> s != null)
                .toList();

        // 并发下载头像 + 高清立绘 + 技能图标（失败降级为 CDN 外链）
        Map<String, String> avatarPathByNid = new ConcurrentHashMap<>();
        Map<String, String> avatar3PathByNid = new ConcurrentHashMap<>();
        Map<String, String> iconPathBySkillId = new ConcurrentHashMap<>();
        List<Runnable> tasks = new ArrayList<>();
        for (NinjaData n : newNinjas) {
            tasks.add(() -> {
                String cdn = avatarCdnUrl(n.szPicUrl2());
                String local = downloadImage(cdn);
                avatarPathByNid.put(n.nid(), local != null ? local : cdn);
            });
            tasks.add(() -> {
                String cdn3 = avatar3CdnUrl(n.szPicUrl3());
                String local3 = downloadImage(cdn3);
                avatar3PathByNid.put(n.nid(), local3 != null ? local3 : cdn3);
            });
        }
        for (SkillData s : newSkills) {
            tasks.add(() -> {
                String cdn = skillIconCdnUrl(s);
                String local = downloadImage(cdn);
                iconPathBySkillId.put(s.skillId(), local != null ? local : cdn);
            });
        }
        runConcurrently(tasks);

        // 先落技能，再落忍者
        int failed = 0;
        for (SkillData s : newSkills) {
            try {
                skillRepository.insert(HyolSkill.builder()
                        .skillId(s.skillId())
                        .title(s.title())
                        .name(s.name())
                        .type(s.type())
                        .moment(s.moment())
                        .description(s.desc())
                        .hurtType(s.hurtType())
                        .chaseStatus(s.chaseStatus())
                        .hurtStatus(s.hurtStatus())
                        .rare(s.rare())
                        .iconUrl(iconPathBySkillId.getOrDefault(s.skillId(), skillIconCdnUrl(s)))
                        .build());
            } catch (Exception e) {
                failed++;
                log.warn("技能入库失败: skillId={}, err={}", s.skillId(), e.getMessage());
            }
        }

        int fetched = 0;
        for (NinjaData n : newNinjas) {
            try {
                ninjaRepository.insert(HyolNinja.builder()
                        .nid(n.nid())
                        .name(n.name())
                        .nickname(n.nickname())
                        .attr(n.attr())
                        .star(n.star())
                        .org(n.org())
                        .pos(n.pos())
                        .getWay(n.getWay())
                        .effect(n.effect())
                        .effectChase(n.effectChase())
                        .avatarUrl(avatarPathByNid.getOrDefault(n.nid(), avatarCdnUrl(n.szPicUrl2())))
                        .avatarUrl3(avatar3PathByNid.getOrDefault(n.nid(), avatar3CdnUrl(n.szPicUrl3())))
                        .skillOy(validSkill(n.skillOy()))
                        .skillPg(validSkill(n.skillPg()))
                        .skillBd1(validSkill(n.skillBd1()))
                        .skillBd2(validSkill(n.skillBd2()))
                        .skillBd3(validSkill(n.skillBd3()))
                        .build());
                fetched++;
            } catch (Exception e) {
                failed++;
                log.warn("忍者入库失败: nid={}, err={}", n.nid(), e.getMessage());
            }
        }

        log.info("忍者图鉴刷新完成: fetched={}, failed={}", fetched, failed);
        return HyolRefreshResponse.builder().fetched(fetched).failed(failed).build();
    }

    @Override
    public IPage<HyolNinjaResponse> list(int page, int size, String keyword, String attr,
                                         String hurtType, String chaseStatus, String hurtStatus, String rare) {
        if (ninjaRepository.selectCount(new LambdaQueryWrapper<>()) == 0) {
            try {
                refresh();
            } catch (Exception e) {
                log.warn("首次自动抓取忍者数据失败: {}", e.getMessage());
            }
        }
        LambdaQueryWrapper<HyolNinja> query = new LambdaQueryWrapper<HyolNinja>()
                .orderByDesc(HyolNinja::getNid);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            query.and(w -> w.like(HyolNinja::getName, kw).or().like(HyolNinja::getNickname, kw));
        }
        if (attr != null && !attr.isBlank()) {
            query.eq(HyolNinja::getAttr, attr.trim());
        }
        // 技能维度筛选：存在至少一个技能同时满足全部已选条件
        if (anySkillFilter(hurtType, chaseStatus, hurtStatus, rare)) {
            List<String> skillIds = matchSkillIds(hurtType, chaseStatus, hurtStatus, rare);
            if (skillIds.isEmpty()) {
                Page<HyolNinjaResponse> empty = new Page<>(page, size);
                empty.setTotal(0);
                return empty;
            }
            query.and(w -> w.in(HyolNinja::getSkillOy, skillIds)
                    .or().in(HyolNinja::getSkillPg, skillIds)
                    .or().in(HyolNinja::getSkillBd1, skillIds)
                    .or().in(HyolNinja::getSkillBd2, skillIds)
                    .or().in(HyolNinja::getSkillBd3, skillIds));
        }
        IPage<HyolNinja> raw = ninjaRepository.selectPage(newPage(page, size), query);

        // 一次性查出本页忍者引用的全部技能
        Set<String> ids = new HashSet<>();
        for (HyolNinja n : raw.getRecords()) {
            collectSkill(ids, n.getSkillOy());
            collectSkill(ids, n.getSkillPg());
            collectSkill(ids, n.getSkillBd1());
            collectSkill(ids, n.getSkillBd2());
            collectSkill(ids, n.getSkillBd3());
        }
        Map<String, HyolSkill> skillById = ids.isEmpty()
                ? Map.of()
                : skillRepository.selectList(new LambdaQueryWrapper<HyolSkill>().in(HyolSkill::getSkillId, ids))
                        .stream().collect(Collectors.toMap(HyolSkill::getSkillId, s -> s, (a, b) -> a));

        return raw.convert(n -> toResponse(n, skillById));
    }

    // ==================== 内部方法 ====================

    private JsonNode fetchJson() {
        byte[] bytes = HttpRequest.post(API_URL)
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .header("Origin", ORIGIN)
                .header("X-Requested-With", "XMLHttpRequest")
                .form("game", "huo")
                .timeout(30000)
                .execute()
                .bodyBytes();
        try {
            return MAPPER.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("解析忍者数据失败", e);
        }
    }

    /** 忍者头像：getAvatarUrl(szPicUrl2) = shp.qpic.cn/tgos/{path.replace('.', '/')}/0 */
    private String avatarCdnUrl(String szPicUrl2) {
        if (szPicUrl2 == null || szPicUrl2.isBlank() || szPicUrl2.contains("http")) {
            return DEFAULT_AVATAR;
        }
        return "https://shp.qpic.cn/tgos/" + szPicUrl2.replace(".", "/") + "/0";
    }

    /** 忍者高清立绘：szPicUrl3，构造方式同 szPicUrl2 */
    private String avatar3CdnUrl(String szPicUrl3) {
        if (szPicUrl3 == null || szPicUrl3.isBlank() || szPicUrl3.contains("http")) {
            return DEFAULT_AVATAR;
        }
        return "https://shp.qpic.cn/tgos/" + szPicUrl3.replace(".", "/") + "/0";
    }

    /** 技能图标：formatImgUrl(skill, "skill") */
    private String skillIconCdnUrl(SkillData s) {
        String szPicUrl = s.szPicUrl();
        if (szPicUrl != null && szPicUrl.contains("Naruto")) {
            return "http://res.huoying.qq.com/" + szPicUrl + "/assets/skill/40/" + s.skillId() + ".png";
        }
        if (szPicUrl != null && !szPicUrl.isBlank()) {
            return "https://shp.qpic.cn/tgos/" + szPicUrl.replace(".", "/") + "/0";
        }
        return DEFAULT_SKILL_ICON;
    }

    private HyolNinjaResponse toResponse(HyolNinja n, Map<String, HyolSkill> skillById) {
        List<HyolNinjaResponse.SkillItem> skills = new ArrayList<>();
        appendSkill(skills, skillById.get(n.getSkillOy()));
        appendSkill(skills, skillById.get(n.getSkillPg()));
        appendSkill(skills, skillById.get(n.getSkillBd1()));
        appendSkill(skills, skillById.get(n.getSkillBd2()));
        appendSkill(skills, skillById.get(n.getSkillBd3()));

        return HyolNinjaResponse.builder()
                .id(n.getId())
                .nid(n.getNid())
                .name(n.getName())
                .nickname(n.getNickname())
                .attr(n.getAttr())
                .star(n.getStar())
                .org(n.getOrg())
                .pos(n.getPos())
                .getWay(n.getGetWay())
                .effect(n.getEffect())
                .effectChase(n.getEffectChase())
                .avatarUrl(n.getAvatarUrl())
                .avatarUrl3(n.getAvatarUrl3())
                .skills(skills)
                .build();
    }

    private void appendSkill(List<HyolNinjaResponse.SkillItem> out, HyolSkill s) {
        if (s == null) {
            return;
        }
        out.add(HyolNinjaResponse.SkillItem.builder()
                .title(s.getTitle())
                .type(s.getType())
                .moment(s.getMoment())
                .desc(s.getDescription())
                .hurtType(s.getHurtType())
                .chaseStatus(s.getChaseStatus())
                .hurtStatus(s.getHurtStatus())
                .rare(s.getRare())
                .iconUrl(s.getIconUrl())
                .build());
    }

    private static void collectSkill(Set<String> out, String skillId) {
        if (skillId != null && !skillId.isBlank() && !"0".equals(skillId)) {
            out.add(skillId);
        }
    }

    /** 空/0 视为无效技能槽，入库为 null */
    private static String validSkill(String skillId) {
        return (skillId != null && !skillId.isBlank() && !"0".equals(skillId)) ? skillId : null;
    }

    private static boolean anySkillFilter(String... filters) {
        for (String f : filters) {
            if (f != null && !f.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** 命中「同时满足全部已选技能条件」的技能 ID 集合 */
    private List<String> matchSkillIds(String hurtType, String chaseStatus, String hurtStatus, String rare) {
        LambdaQueryWrapper<HyolSkill> q = new LambdaQueryWrapper<HyolSkill>().select(HyolSkill::getSkillId);
        if (hurtType != null && !hurtType.isBlank()) {
            q.like(HyolSkill::getHurtType, hurtType.trim());
        }
        if (chaseStatus != null && !chaseStatus.isBlank()) {
            q.like(HyolSkill::getChaseStatus, chaseStatus.trim());
        }
        if (hurtStatus != null && !hurtStatus.isBlank()) {
            q.like(HyolSkill::getHurtStatus, hurtStatus.trim());
        }
        if (rare != null && !rare.isBlank()) {
            q.like(HyolSkill::getRare, rare.trim());
        }
        return skillRepository.selectList(q).stream().map(HyolSkill::getSkillId).toList();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private void runConcurrently(List<Runnable> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        List<Future<?>> futures = tasks.stream().<Future<?>>map(IMAGE_POOL::submit).toList();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("忍者图片并发下载被中断: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("忍者图片下载任务异常: {}", e.getMessage());
            }
        }
    }

    /** 下载图片到本地并返回本地路径；失败返回 null（调用方降级为 CDN 外链） */
    private String downloadImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String name = SecureUtil.md5(url) + extOf(url);
            Path file = storageConfig.getDataPath().resolve(IMAGE_DIR).resolve(name);
            if (Files.exists(file)) {
                return "/api/hyol/ninja/image/" + name;
            }
            byte[] bytes = HttpRequest.get(url)
                    .header("User-Agent", UA)
                    .header("Referer", REFERER)
                    .timeout(20000)
                    .execute()
                    .bodyBytes();
            if (bytes.length == 0) {
                return null;
            }
            byte[] stored = compressImage(bytes);
            Files.createDirectories(file.getParent());
            Files.write(file, stored);
            return "/api/hyol/ninja/image/" + name;
        } catch (Exception e) {
            log.warn("忍者图片下载失败: url={}, err={}", url, e.getMessage());
            return null;
        }
    }

    /** 感知无损压缩：非 GIF、非透明、像素可控的大图重编码为 JPEG，仅当更小时才采用 */
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
            log.warn("忍者图片压缩失败，保留原图: {}", e.getMessage());
            return raw;
        }
    }

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

    private Page<HyolNinja> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private record NinjaData(String nid, String name, String nickname, String attr, String star,
                             String org, String pos, String getWay, String effect, String effectChase,
                             String szPicUrl2, String szPicUrl3, String skillOy, String skillPg, String skillBd1,
                             String skillBd2, String skillBd3) {
    }

    private record SkillData(String skillId, String title, String name, String type,
                             String moment, String desc, String hurtType, String chaseStatus,
                             String hurtStatus, String rare, String szPicUrl) {
    }
}
