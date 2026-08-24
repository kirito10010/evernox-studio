package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.config.StorageConfig;
import com.evernox.dto.ImageResponse;
import com.evernox.dto.ImageUploadRequest;
import com.evernox.dto.StorageStatsResponse;
import com.evernox.entity.Image;
import com.evernox.entity.ImageAlbum;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.ImageAlbumRepository;
import com.evernox.repository.ImageRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.ImageService;
import com.evernox.util.ImageCodec;
import com.evernox.util.ImageTypeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * 图片服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;
    private final ImageAlbumRepository imageAlbumRepository;
    private final UserRepository userRepository;
    private final StorageConfig storageConfig;
    private final ImageCodec imageCodec;

    /** 可解码处理的最大像素数，超过则跳过压缩与缩略图，避免超大图在低内存服务器上 OOM */
    private static final int MAX_PROCESS_PIXELS = 30_000_000;

    /** 原图感知无损压缩的 JPEG 质量 */
    private static final float JPEG_COMPRESSION_QUALITY = 0.92f;

    @Override
    @Transactional
    public ImageResponse uploadImage(MultipartFile file, ImageUploadRequest request, Long userId) {
        try {
            byte[] raw = file.getBytes();

            // 服务端能看到真实字节，按魔数校验实际类型，避免伪造 mimeType
            String detectedMime = ImageTypeValidator.detectMimeType(raw);
            if (detectedMime == null) {
                throw new BusinessException("不支持的文件类型，仅允许上传图片");
            }

            // 确保用户目录存在
            storageConfig.ensureUserDirs(userId);

            // 封面与笔记插图各走独立目录，避免和用户自己上传的照片混在一起
            int purpose = normalizePurpose(request.getPurpose());
            Path targetDir = switch (purpose) {
                case StorageConfig.PURPOSE_PHOTO -> storageConfig.getImagePath(userId);
                case StorageConfig.PURPOSE_NOTE_IMAGE -> storageConfig.getNoteImagePath(userId);
                case StorageConfig.PURPOSE_ANNOUNCEMENT -> storageConfig.getAnnouncementImagePath(userId);
                case StorageConfig.PURPOSE_TOPIC_IMAGE -> storageConfig.getTopicImagePath(userId);
                default -> storageConfig.getCoverPath(userId, purpose);
            };
            Files.createDirectories(targetDir);

            // 仅当尺寸在安全阈值内才解码（用于感知无损压缩与缩略图），避免超大图 OOM
            BufferedImage src = null;
            int[] dims = readImageDimensions(raw);
            if (dims != null && (long) dims[0] * dims[1] <= MAX_PROCESS_PIXELS) {
                src = ImageIO.read(new ByteArrayInputStream(raw));
            }

            // 感知无损压缩：无透明位图重编码为 JPEG 0.92，体积更小才采用
            byte[] finalBytes = raw;
            String finalMime = detectedMime;
            if (src != null && !"image/gif".equals(detectedMime) && !src.getColorModel().hasAlpha()) {
                try {
                    byte[] compressed = encodeJpeg(src, JPEG_COMPRESSION_QUALITY);
                    if (compressed != null && compressed.length > 0 && compressed.length < raw.length) {
                        finalBytes = compressed;
                        finalMime = "image/jpeg";
                    }
                } catch (Exception e) {
                    log.warn("原图感知压缩失败，保留原图: {}", e.getMessage());
                }
            }

            // 服务端编码后落盘：磁盘上的文件不是合法图片，改后缀也无法查看
            ImageCodec.Encoded encoded = imageCodec.encode(finalBytes);

            String fileName = UUID.randomUUID().toString() + ".evx";
            Path imagePath = targetDir.resolve(fileName);
            Files.write(imagePath, encoded.payload());

            // 构建图片实体
            Image image = Image.builder()
                    .userId(userId)
                    .originalName(request.getOriginalName())
                    .storagePath(imagePath.toString())
                    .fileSize((long) finalBytes.length)
                    .mimeType(finalMime)
                    .width(request.getWidth())
                    .height(request.getHeight())
                    .iv(encoded.ivHex())
                    .visibility(request.getVisibility() != null ? request.getVisibility() : 0)
                    .purpose(purpose)
                    .deleted(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // 缩略图从已解码的原图生成（失败仅跳过，不影响上传主流程）
            buildThumbnail(src, image, userId);

            imageRepository.insert(image);

            // 如果指定了相册，创建关联
            if (request.getAlbumId() != null) {
                ImageAlbum imageAlbum = ImageAlbum.builder()
                        .imageId(image.getId())
                        .albumId(request.getAlbumId())
                        .createdAt(LocalDateTime.now())
                        .build();
                imageAlbumRepository.insert(imageAlbum);
            }

            log.info("图片上传成功: id={}, user={}, purpose={}, file={}",
                    image.getId(), userId, purpose, request.getOriginalName());

            ImageResponse response = ImageResponse.from(image);
            response.setUploaderName(getUsername(userId));
            return response;

        } catch (IOException e) {
            log.error("保存图片文件失败: {}", e.getMessage(), e);
            throw new BusinessException("保存图片文件失败");
        }
    }

    @Override
    public IPage<ImageResponse> getUserImages(Long userId, Page<Image> page) {
        LambdaQueryWrapper<Image> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Image::getUserId, userId)
               .eq(Image::getPurpose, StorageConfig.PURPOSE_PHOTO)
               .orderByDesc(Image::getCreatedAt);

        IPage<Image> result = imageRepository.selectPage(page, wrapper);

        return result.convert(image -> {
            ImageResponse resp = ImageResponse.from(image);
            resp.setUploaderName(getUsername(userId));
            return resp;
        });
    }

    @Override
    public IPage<ImageResponse> getPublicImages(Page<Image> page) {
        LambdaQueryWrapper<Image> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Image::getVisibility, 1)
               .eq(Image::getPurpose, StorageConfig.PURPOSE_PHOTO)
               .orderByDesc(Image::getCreatedAt);

        IPage<Image> result = imageRepository.selectPage(page, wrapper);

        return result.convert(image -> {
            ImageResponse resp = ImageResponse.from(image);
            resp.setUploaderName(getUsername(image.getUserId()));
            return resp;
        });
    }

    @Override
    public ImageResponse getImageById(Long id, Long userId) {
        Image image = imageRepository.selectById(id);
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        // 私密图片只有所有者能查看
        if (image.getVisibility() == 0 && !image.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该图片");
        }
        ImageResponse resp = ImageResponse.from(image);
        resp.setUploaderName(getUsername(image.getUserId()));
        return resp;
    }

    @Override
    public Resource getImageFile(Long id, Long userId) {
        Image image = imageRepository.selectById(id);
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        // 私密图片只有所有者能获取
        if (image.getVisibility() == 0 && !image.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该图片");
        }

        Path filePath = Paths.get(image.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException("图片文件不存在");
        }

        try {
            byte[] stored = Files.readAllBytes(filePath);
            // 统一由服务端解码，返回原始图片字节
            byte[] raw = imageCodec.decode(stored, image.getIv());
            return new ByteArrayResource(raw);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取图片文件失败: {}", e.getMessage(), e);
            throw new BusinessException("读取图片文件失败");
        }
    }

    @Override
    public Resource getThumbnailFile(Long id, Long userId) {
        Image image = imageRepository.selectById(id);
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        if (image.getVisibility() == 0 && !image.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该图片");
        }
        // 无缩略图或文件缺失：回退原图
        if (image.getThumbnailPath() == null || image.getThumbnailIv() == null) {
            return getImageFile(id, userId);
        }
        Path thumbPath = Paths.get(image.getThumbnailPath());
        if (!Files.exists(thumbPath)) {
            return getImageFile(id, userId);
        }

        try {
            byte[] stored = Files.readAllBytes(thumbPath);
            byte[] raw = imageCodec.decode(stored, image.getThumbnailIv());
            return new ByteArrayResource(raw);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取缩略图失败: {}", e.getMessage(), e);
            return getImageFile(id, userId);
        }
    }

    @Override
    public Resource getAnnouncementImageFile(Long id, Long userId) {
        return readContentImage(id, StorageConfig.PURPOSE_ANNOUNCEMENT);
    }

    @Override
    public String getAnnouncementImageMimeType(Long id) {
        return requireContentImage(id, StorageConfig.PURPOSE_ANNOUNCEMENT).getMimeType();
    }

    @Override
    public Resource getTopicImageFile(Long id, Long userId) {
        return readContentImage(id, StorageConfig.PURPOSE_TOPIC_IMAGE);
    }

    @Override
    public String getTopicImageMimeType(Long id) {
        return requireContentImage(id, StorageConfig.PURPOSE_TOPIC_IMAGE).getMimeType();
    }

    /** 加载并校验「内容插图」（公告/话题帖子等公开内容图），仅放行指定用途 */
    private Image requireContentImage(Long id, int expectedPurpose) {
        Image image = imageRepository.selectById(id);
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        if (image.getPurpose() == null || image.getPurpose() != expectedPurpose) {
            throw new BusinessException(403, "无权访问该图片");
        }
        return image;
    }

    /** 读取内容插图文件字节并解码 */
    private Resource readContentImage(Long id, int expectedPurpose) {
        Image image = requireContentImage(id, expectedPurpose);
        Path filePath = Paths.get(image.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException("图片文件不存在");
        }
        try {
            byte[] stored = Files.readAllBytes(filePath);
            byte[] raw = imageCodec.decode(stored, image.getIv());
            return new ByteArrayResource(raw);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取内容插图失败: {}", e.getMessage(), e);
            throw new BusinessException("读取图片文件失败");
        }
    }

    @Override
    @Transactional
    public void deleteImage(Long id, Long userId) {
        Image image = imageRepository.selectById(id);
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该图片");
        }

        // 逻辑删除
        imageRepository.deleteById(id);

        // 删除关联的相册关系
        LambdaQueryWrapper<ImageAlbum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageAlbum::getImageId, id);
        imageAlbumRepository.delete(wrapper);

        // 删除物理文件
        try {
            Path filePath = Paths.get(image.getStoragePath());
            Files.deleteIfExists(filePath);
            if (image.getThumbnailPath() != null) {
                Path thumbPath = Paths.get(image.getThumbnailPath());
                Files.deleteIfExists(thumbPath);
            }
        } catch (IOException e) {
            log.warn("删除图片物理文件失败: {}", e.getMessage());
        }

        log.info("图片删除成功: id={}, user={}", id, userId);
    }

    @Override
    public long countUserImages(Long userId) {
        LambdaQueryWrapper<Image> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Image::getUserId, userId)
               .eq(Image::getPurpose, StorageConfig.PURPOSE_PHOTO);
        return imageRepository.selectCount(wrapper);
    }

    @Override
    public long countPublicImages() {
        LambdaQueryWrapper<Image> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Image::getVisibility, 1)
               .eq(Image::getPurpose, StorageConfig.PURPOSE_PHOTO);
        return imageRepository.selectCount(wrapper);
    }

    @Override
    @Transactional
    public ImageResponse updateVisibility(Long id, Long userId, Integer visibility) {
        Image image = imageRepository.selectById(id);
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权修改该图片");
        }

        image.setVisibility(visibility);
        image.setUpdatedAt(LocalDateTime.now());
        imageRepository.updateById(image);

        ImageResponse resp = ImageResponse.from(image);
        resp.setUploaderName(getUsername(userId));
        log.info("图片可见性更新: id={}, visibility={}", id, visibility);
        return resp;
    }

    @Override
    @Transactional
    public void setVisibilityBySystem(Long imageId, Integer visibility) {
        if (imageId == null) {
            return;
        }
        Image image = imageRepository.selectById(imageId);
        // 封面可能已被所有者删除，此时不应中断调用方的审批事务
        if (image == null || visibility.equals(image.getVisibility())) {
            return;
        }
        image.setVisibility(visibility);
        image.setUpdatedAt(LocalDateTime.now());
        imageRepository.updateById(image);
        log.info("图片可见性系统级更新: id={}, visibility={}", imageId, visibility);
    }


    @Override
    @Transactional
    public void updateDimensions(Long id, Long userId, Integer width, Integer height) {
        Image image = imageRepository.selectById(id);
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权修改该图片");
        }
        // 值已一致就不写库，避免前端每次渲染都触发无意义 UPDATE
        if (width.equals(image.getWidth()) && height.equals(image.getHeight())) {
            return;
        }

        log.info("图片尺寸校正: id={}, {}x{} -> {}x{}",
                id, image.getWidth(), image.getHeight(), width, height);
        image.setWidth(width);
        image.setHeight(height);
        image.setUpdatedAt(LocalDateTime.now());
        imageRepository.updateById(image);
    }


    @Override
    public java.util.List<Long> getImageAlbumIds(Long imageId) {
        LambdaQueryWrapper<ImageAlbum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageAlbum::getImageId, imageId);
        java.util.List<ImageAlbum> relations = imageAlbumRepository.selectList(wrapper);
        return relations.stream().map(ImageAlbum::getAlbumId).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public StorageStatsResponse getStorageStats(Long userId) {
        // 数据目录所在磁盘容量：jar 部署时即 jar 运行目录所在盘
        File dataRoot = storageConfig.getDataPath().toFile();
        long diskTotal = dataRoot.getTotalSpace();
        // usable 而非 free：排除系统保留块，反映真正可写入的空间
        long diskFree = dataRoot.getUsableSpace();
        long diskUsed = diskTotal > 0 ? diskTotal - diskFree : 0L;

        return StorageStatsResponse.builder()
                .diskTotal(diskTotal)
                .diskFree(diskFree)
                .diskUsed(diskUsed)
                .imagesUsed(sumUserImageSize(userId))
                .allImagesUsed(sumAllImageSize())
                .imagesCount(countUserImages(userId))
                .build();
    }

    /**
     * 汇总用户所有图片的 file_size（逻辑删除条件由 MyBatis-Plus 自动追加）
     *
     * 这里刻意不按 purpose 过滤：封面同样占磁盘，容量口径必须把它算进来，
     * 与"张数只算图床照片"的口径不同。
     */
    private long sumUserImageSize(Long userId) {
        QueryWrapper<Image> wrapper = new QueryWrapper<>();
        wrapper.select("IFNULL(SUM(file_size), 0) AS total").eq("user_id", userId);
        return firstLong(imageRepository.selectMaps(wrapper));
    }

    /** 汇总全平台图片的 file_size：占用率的分母要用它，而不是单个用户的占用 */
    private long sumAllImageSize() {
        QueryWrapper<Image> wrapper = new QueryWrapper<>();
        wrapper.select("IFNULL(SUM(file_size), 0) AS total");
        return firstLong(imageRepository.selectMaps(wrapper));
    }

    private long firstLong(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return 0L;
        }
        Object total = rows.get(0).get("total");
        return total instanceof Number ? ((Number) total).longValue() : 0L;
    }

    /** purpose 来自请求体，非法值一律按普通照片处理，避免脏值把文件写到意外目录 */
    private int normalizePurpose(Integer purpose) {
        if (purpose == null) {
            return StorageConfig.PURPOSE_PHOTO;
        }
        return switch (purpose) {
            case StorageConfig.PURPOSE_ALBUM_COVER,
                 StorageConfig.PURPOSE_SITE_COVER,
                 StorageConfig.PURPOSE_NOTE_IMAGE,
                 StorageConfig.PURPOSE_ANNOUNCEMENT,
                 StorageConfig.PURPOSE_TOPIC_IMAGE -> purpose;
            default -> StorageConfig.PURPOSE_PHOTO;
        };
    }

    /** 生成缩略图并编码落盘；src 为已解码原图，失败或无需生成时静默返回 */
    private void buildThumbnail(BufferedImage src, Image image, Long userId) {
        try {
            if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
                return;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            int maxWidth = storageConfig.getThumbnail().getMaxWidth();
            // 原图已足够小：不生成缩略图（读取时回退原图）
            if (w <= maxWidth) {
                return;
            }
            int newH = Math.max(1, (int) Math.round(h * ((double) maxWidth / w)));

            BufferedImage scaled = new BufferedImage(maxWidth, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // 透明图合成到白底，避免 JPEG 透明区域变黑
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, maxWidth, newH);
            g.drawImage(src, 0, 0, maxWidth, newH, null);
            g.dispose();

            byte[] thumbBytes = encodeJpeg(scaled, (float) storageConfig.getThumbnail().getQuality());
            if (thumbBytes == null || thumbBytes.length == 0) {
                return;
            }
            ImageCodec.Encoded encoded = imageCodec.encode(thumbBytes);
            Path thumbPath = storageConfig.getThumbnailPath(userId).resolve(UUID.randomUUID().toString() + ".evx");
            Files.write(thumbPath, encoded.payload());
            image.setThumbnailPath(thumbPath.toString());
            image.setThumbnailIv(encoded.ivHex());
        } catch (Exception e) {
            log.warn("生成缩略图失败: {}", e.getMessage());
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

    private String getUsername(Long userId) {
        User user = userRepository.selectById(userId);
        return user != null ? user.getUsername() : "未知用户";
    }
}
