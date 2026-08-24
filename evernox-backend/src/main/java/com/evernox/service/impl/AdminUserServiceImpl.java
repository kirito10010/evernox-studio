package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.ResultCode;
import com.evernox.common.UserRole;
import com.evernox.config.StorageConfig;
import com.evernox.dto.AdminUserCreateRequest;
import com.evernox.dto.AdminUserUpdateRequest;
import com.evernox.dto.UserInfoResponse;
import com.evernox.dto.UserOptionResponse;
import com.evernox.dto.UserStatsResponse;
import com.evernox.entity.Album;
import com.evernox.entity.Image;
import com.evernox.entity.ImageAlbum;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.AlbumRepository;
import com.evernox.repository.ImageAlbumRepository;
import com.evernox.repository.ImageRepository;
import com.evernox.repository.UserRepository;
import com.evernox.security.Argon2idPasswordEncoder;
import com.evernox.service.AdminUserService;
import com.evernox.util.SortColumnResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 管理员用户管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final AlbumRepository albumRepository;
    private final ImageAlbumRepository imageAlbumRepository;
    private final Argon2idPasswordEncoder passwordEncoder;
    private final StorageConfig storageConfig;

    /** 排序字段白名单：前端传入的字符串绝不能直接拼进 SQL */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "created_at",
            "lastLoginAt", "last_login_at",
            "points", "points",
            "username", "username"
    );

    /** 本模块可分配的角色，admin 不在其中 */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(UserRole.MEMBER, UserRole.SUPER_MEMBER);

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public IPage<UserInfoResponse> listUsers(int page, int size, String keyword, String role, Integer status,
                                             String startDate, String endDate, String sortField, String sortOrder) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.ne("role", UserRole.ADMIN);

        // 嵌套 and(...) 不可省：平铺的 or 会把上面的 role<>admin 短路掉，导致管理员账号在搜索时泄漏
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("username", kw).or().like("email", kw));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq("role", role);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }

        LocalDate start = parseDate(startDate);
        if (start != null) {
            wrapper.ge("created_at", start.atStartOfDay());
        }
        LocalDate end = parseDate(endDate);
        if (end != null) {
            // 上界取次日 00:00 的开区间，避免 created_at 带时分秒时漏掉当天记录
            wrapper.lt("created_at", end.plusDays(1).atStartOfDay());
        }

        String column = SortColumnResolver.resolve(SORT_COLUMNS, sortField, "created_at");
        wrapper.orderBy(true, "asc".equalsIgnoreCase(sortOrder), column);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<User> pageParam = new Page<>(Math.max(page, 1), safeSize);
        IPage<User> result = userRepository.selectPage(pageParam, wrapper);

        return result.convert(UserInfoResponse::from);
    }

    @Override
    public UserStatsResponse getStats() {
        return UserStatsResponse.builder()
                .total(countBy(w -> w.ne("role", UserRole.ADMIN)))
                .members(countBy(w -> w.eq("role", UserRole.MEMBER)))
                .superMembers(countBy(w -> w.eq("role", UserRole.SUPER_MEMBER)))
                .disabled(countBy(w -> w.ne("role", UserRole.ADMIN).eq("status", 0)))
                .build();
    }

    @Override
    public List<UserOptionResponse> listUserOptions() {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.select("id", "username").orderByAsc("username");
        return userRepository.selectList(wrapper).stream()
                .map(user -> UserOptionResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .build())
                .toList();
    }

    @Override
    public UserInfoResponse getUser(Long id) {
        return UserInfoResponse.from(requireManageableUser(id));
    }

    @Override
    @Transactional
    public UserInfoResponse createUser(AdminUserCreateRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim();
        validateAssignableRole(request.getRole());

        if (countBy(w -> w.eq("username", username)) > 0) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }
        if (countBy(w -> w.eq("email", email)) > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .email(email)
                .role(request.getRole())
                .status(normalizeStatus(request.getStatus()))
                .points(request.getPoints())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.insert(user);
        log.info("管理员创建用户: id={}, username={}, role={}", user.getId(), username, user.getRole());
        return UserInfoResponse.from(user);
    }

    @Override
    @Transactional
    public UserInfoResponse updateUser(Long id, AdminUserUpdateRequest request) {
        User user = requireManageableUser(id);
        validateAssignableRole(request.getRole());

        String email = request.getEmail().trim();
        if (!email.equals(user.getEmail())
                && countBy(w -> w.eq("email", email).ne("id", id)) > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        user.setEmail(email);
        user.setRole(request.getRole());
        user.setStatus(normalizeStatus(request.getStatus()));
        user.setPoints(request.getPoints());
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.updateById(user);
        log.info("管理员更新用户: id={}, username={}, role={}, status={}",
                id, user.getUsername(), user.getRole(), user.getStatus());
        return UserInfoResponse.from(user);
    }

    @Override
    @Transactional
    public UserInfoResponse updateStatus(Long id, Integer status) {
        User user = requireManageableUser(id);
        user.setStatus(normalizeStatus(status));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
        log.info("管理员修改用户状态: id={}, username={}, status={}", id, user.getUsername(), user.getStatus());
        return UserInfoResponse.from(user);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String password) {
        User user = requireManageableUser(id);
        user.setPassword(passwordEncoder.encode(password));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
        // 只记 username，不记明文密码
        log.warn("管理员重置用户密码: id={}, username={}", id, user.getUsername());
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = requireManageableUser(id);
        int imageCount = cascadeDelete(id);
        userRepository.deleteById(id);
        log.warn("管理员删除用户: id={}, username={}, 关联图片={}", id, user.getUsername(), imageCount);
        // 文件删除放在数据操作之后：删文件失败不应回滚账号删除
        deleteUserDirs(id);
    }

    @Override
    @Transactional
    public void deleteUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "未选择要删除的用户");
        }
        // 先整批校验：含管理员则全部拒绝，避免管理员误以为全删成功
        List<User> targets = ids.stream().map(this::requireManageableUser).toList();

        for (User user : targets) {
            int imageCount = cascadeDelete(user.getId());
            userRepository.deleteById(user.getId());
            log.warn("管理员批量删除用户: id={}, username={}, 关联图片={}",
                    user.getId(), user.getUsername(), imageCount);
        }
        targets.forEach(user -> deleteUserDirs(user.getId()));
    }

    // ==================== 私有方法 ====================

    /**
     * 加载可管理的目标用户
     *
     * 不存在与「是管理员」都不透露具体原因之外的信息：前者 1001，后者 403。
     */
    private User requireManageableUser(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (UserRole.ADMIN.equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不允许操作管理员账号");
        }
        return user;
    }

    private void validateAssignableRole(String role) {
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色只能是普通成员或超级会员");
        }
    }

    private int normalizeStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能是0或1");
        }
        return status;
    }

    private long countBy(java.util.function.Consumer<QueryWrapper<User>> customizer) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        customizer.accept(wrapper);
        return userRepository.selectCount(wrapper);
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }

    /**
     * 删除用户名下的图片、相册及关联记录
     *
     * @return 被删除的图片数量
     */
    @SuppressWarnings("null")
    private int cascadeDelete(Long userId) {
        List<Long> imageIds = imageRepository
                .selectList(new LambdaQueryWrapper<Image>().eq(Image::getUserId, userId))
                .stream()
                .map(Image::getId)
                .toList();

        if (!imageIds.isEmpty()) {
            imageAlbumRepository.delete(
                    new LambdaQueryWrapper<ImageAlbum>().in(ImageAlbum::getImageId, imageIds));
            imageRepository.delete(new LambdaQueryWrapper<Image>().eq(Image::getUserId, userId));
        }
        albumRepository.delete(new LambdaQueryWrapper<Album>().eq(Album::getUserId, userId));
        return imageIds.size();
    }

    /**
     * 删除用户的图片与缩略图目录
     *
     * 路径由 StorageConfig 依据 userId 生成，不接受外部字符串拼接。
     * 失败只记日志：账号已删除是主要目标，残留文件由后续清理处理。
     */
    private void deleteUserDirs(Long userId) {
        deleteRecursively(storageConfig.getImagePath(userId));
        deleteRecursively(storageConfig.getThumbnailPath(userId));
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.error("删除文件失败: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.error("遍历目录失败: {}", dir, e);
        }
    }
}
