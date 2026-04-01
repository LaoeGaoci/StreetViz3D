package com.streetviz3d.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.streetviz3d.backend.dto.request.UpdatePasswordRequest;
import com.streetviz3d.backend.dto.request.UserLoginRequest;
import com.streetviz3d.backend.dto.request.UserRegisterRequest;
import com.streetviz3d.backend.dto.response.LoginResponse;
import com.streetviz3d.backend.dto.response.UserIdResponse;
import com.streetviz3d.backend.dto.response.UserInfoResponse;
import com.streetviz3d.backend.entity.AppUser;
import com.streetviz3d.backend.mapper.AppUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.streetviz3d.backend.dto.response.UserStreetListItemResponse;
import com.streetviz3d.backend.entity.Street;
import com.streetviz3d.backend.mapper.StreetMapper;
import java.util.List;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserMapper appUserMapper;
    private final StreetMapper streetMapper;

    @Value("${app.nginx.base-url}")
    private String nginxBaseUrl;

    @Value("${app.nginx.upload-dir}")
    private String nginxUploadDir;

    // 数据库存储的默认头像相对路径
    private static final String DEFAULT_AVATAR_PATH = "default/default-avatar.png";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp"
    );
    /**
     * 用户注册
     * 业务流程：
     * 1. 校验邮箱是否已被注册；
     * 2. 校验用户名是否已存在；
     * 3. 创建用户对象；
     * 4. 为用户生成 UUID；
     * 5. 设置默认头像相对路径；
     * 6. 写入数据库。
     *
     * @param request 注册请求参数，包含用户名、邮箱、密码等信息
     * @throws RuntimeException 当邮箱已被注册或用户名已存在时抛出异常
     */
    public void register(UserRegisterRequest request) {
        LambdaQueryWrapper<AppUser> emailWrapper = Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getEmail, request.getEmail());

        AppUser existedByEmail = appUserMapper.selectOne(emailWrapper);
        if (existedByEmail != null) {
            throw new RuntimeException("邮箱已被注册");
        }

        LambdaQueryWrapper<AppUser> userNameWrapper = Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getUserName, request.getUserName());

        AppUser existedByUserName = appUserMapper.selectOne(userNameWrapper);
        if (existedByUserName != null) {
            throw new RuntimeException("用户名已存在");
        }

        AppUser user = new AppUser();
        user.setUserId(UUID.randomUUID().toString());
        user.setUserName(request.getUserName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword());
        user.setAvatarUrl(DEFAULT_AVATAR_PATH);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        appUserMapper.insert(user);
    }
    /**
     * 用户登录
     * 业务流程：
     * 1. 根据邮箱查询用户；
     * 2. 若用户不存在则抛出异常；
     * 3. 校验密码是否正确；
     * 4. 登录成功后返回用户基本信息。
     * 当前说明：
     * 该方法目前未接入 token / session / JWT，
     * 仅完成最基础的账号密码校验。
     *
     * @param request 登录请求参数
     * @return 登录成功后的响应对象，包含 userId、userName、email
     * @throws RuntimeException 当用户不存在或密码错误时抛出异常
     */
    public LoginResponse login(UserLoginRequest request) {
        LambdaQueryWrapper<AppUser> wrapper = Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getEmail, request.getEmail());

        AppUser user = appUserMapper.selectOne(wrapper);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!user.getPasswordHash().equals(request.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        return new LoginResponse(
                user.getUserId(),
                user.getUserName(),
                user.getEmail()
        );
    }
    /**
     * 用户退出登录
     * 当前说明：
     * 由于当前系统尚未接入 token / session 认证体系，
     * 因此该方法暂时不执行实际清理逻辑，直接幂等返回。
     */
    public void logout() {
        // 当前没有 token / session 体系，这里直接幂等返回成功
    }
    /**
     * 根据邮箱查询用户 ID
     * 业务流程：
     * 1. 根据邮箱查询用户；
     * 2. 若用户不存在则抛出异常；
     * 3. 返回用户 ID。
     *
     * @param email 用户邮箱
     * @return 仅包含 userId 的响应对象
     * @throws RuntimeException 当用户不存在时抛出异常
     */
    public UserIdResponse getUserIdByEmail(String email) {
        LambdaQueryWrapper<AppUser> wrapper = Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getEmail, email);

        AppUser user = appUserMapper.selectOne(wrapper);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return new UserIdResponse(user.getUserId());
    }
    /**
     * 获取用户详细信息
     * 业务流程：
     * 1. 根据 userId 查询用户；
     * 2. 若用户不存在则抛出异常；
     * 3. 将数据库中的头像相对路径拼接为完整访问地址；
     * 4. 返回用户信息。
     *
     * @param userId 用户唯一标识
     * @return 用户信息响应对象
     * @throws RuntimeException 当用户不存在时抛出异常
     */
    public UserInfoResponse getUserInfo(String userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return new UserInfoResponse(
                user.getUserId(),
                user.getUserName(),
                buildAvatarUrl(user.getAvatarUrl()),
                user.getEmail(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
    /**
     * 上传用户头像
     * 业务流程：
     * 1. 根据 userId 查询用户是否存在；
     * 2. 校验上传文件是否为空；
     * 3. 校验文件 MIME 类型是否在允许范围内；
     * 4. 根据原文件名或 MIME 类型解析扩展名；
     * 5. 生成数据库相对路径 avatar/{userId}.{ext}；
     * 6. 将文件保存到 Nginx 静态资源目录下的 avatar 文件夹；
     * 7. 删除用户历史头像文件，避免产生旧文件残留；
     * 8. 更新数据库中的 avatar_url 字段；
     * 9. 返回完整头像访问地址。
     * 注意：
     * 数据库中只保存相对路径，例如： avatar/123456.png
     * 对外返回时再拼接为： http://localhost:65/auth/avatar/123456.png
     *
     * @param userId 用户唯一标识
     * @param file 用户上传的头像文件
     * @return 完整头像访问 URL
     * @throws RuntimeException 当用户不存在、文件为空、文件类型非法、文件保存失败时抛出异常
     */
    public String uploadAvatar(String userId, MultipartFile file) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            throw new RuntimeException("无法识别文件类型");
        }

        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new RuntimeException("仅支持 png、jpg、jpeg、webp 图片格式");
        }

        String extension = resolveExtension(file, normalizedContentType);
        String relativePath = "avatar/" + userId + "." + extension;

        Path avatarDir = Paths.get(nginxUploadDir, "avatar");
        Path targetPath = avatarDir.resolve(userId + "." + extension);

        try {
            Files.createDirectories(avatarDir);

            // 删除该用户旧头像文件（保留 default 头像不动）
            deleteOldAvatarFiles(userId);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("头像文件保存失败", e);
        }

        user.setAvatarUrl(relativePath);
        user.setUpdatedAt(OffsetDateTime.now());
        appUserMapper.updateById(user);

        return buildAvatarUrl(relativePath);
    }
    /**
     * 修改用户密码
     * 业务流程：
     * 1. 根据 userId 查询用户；
     * 2. 校验用户是否存在；
     * 3. 校验旧密码是否正确；
     * 4. 更新为新密码；
     * 5. 更新修改时间；
     * 6. 写回数据库。
     * 注意：
     * 当前逻辑中 passwordHash 实际上仍然存的是明文密码，
     * 只是字段名叫 passwordHash。
     * 后续建议接入 BCrypt 等加密方案。
     *
     * @param request 修改密码请求参数
     * @throws RuntimeException 当用户不存在或旧密码错误时抛出异常
     */
    public void updatePassword(UpdatePasswordRequest request) {
        AppUser user = appUserMapper.selectById(request.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!user.getPasswordHash().equals(request.getOldPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        user.setPasswordHash(request.getNewPassword());
        user.setUpdatedAt(OffsetDateTime.now());
        appUserMapper.updateById(user);
    }
    /**
     * 构建头像完整访问地址
     * 处理逻辑：
     * 1. 先对数据库中保存的相对路径进行标准化；
     * 2. 再拼接 Nginx 对外访问地址前缀；
     * 3. 最终返回完整 URL。
     *
     * @param avatarPath 数据库中保存的头像相对路径
     * @return 头像完整访问地址
     */
    private String buildAvatarUrl(String avatarPath) {
        String normalizedPath = normalizeAvatarPath(avatarPath);
        return nginxBaseUrl + "/auth/" + normalizedPath;
    }
    /**
     * 标准化头像相对路径
     * 处理逻辑：
     * 1. 若路径为空，则返回默认头像路径；
     * 2. 统一将反斜杠替换为正斜杠；
     * 3. 若误传了 /auth/ 前缀，则剥离该前缀；
     * 4. 去除多余的开头斜杠；
     * 5. 若最终为空，则仍返回默认头像路径。
     *
     * @param avatarPath 原始头像路径
     * @return 标准化后的相对路径
     */
    private String normalizeAvatarPath(String avatarPath) {
        if (avatarPath == null || avatarPath.isBlank()) {
            return DEFAULT_AVATAR_PATH;
        }

        String normalized = avatarPath.trim().replace("\\", "/");

        if (normalized.startsWith("/auth/")) {
            normalized = normalized.substring("/auth/".length());
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized.isBlank() ? DEFAULT_AVATAR_PATH : normalized;
    }
    /**
     * 解析头像文件扩展名
     * 处理优先级：
     * 1. 优先从原始文件名中提取扩展名；
     * 2. 若文件名中扩展名合法，则直接使用；
     * 3. 若文件名无扩展名或扩展名非法，则根据 MIME 类型推断扩展名。
     * 允许的扩展名：
     * png / jpg / jpeg / webp
     *
     * @param file 上传文件对象
     * @param contentType 标准化后的 MIME 类型
     * @return 文件扩展名，不带点号
     * @throws RuntimeException 当格式不支持时抛出异常
     */
    private String resolveExtension(MultipartFile file, String contentType) {
        String originalFilename = file.getOriginalFilename();
        String extFromFilename = getExtension(originalFilename);

        if (StringUtils.hasText(extFromFilename)) {
            String lowerExt = extFromFilename.toLowerCase(Locale.ROOT);
            if (lowerExt.equals("png") || lowerExt.equals("jpg")
                    || lowerExt.equals("jpeg") || lowerExt.equals("webp")) {
                return lowerExt;
            }
        }

        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new RuntimeException("不支持的图片格式");
        };
    }
    /**
     * 从文件名中提取扩展名
     * 例如：
     * avatar.png -> png
     * test.jpg -> jpg
     * 若文件名为空、没有点号、点号在末尾，则返回空字符串。
     *
     * @param filename 原始文件名
     * @return 文件扩展名，不带点号
     */
    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1);
    }
    /**
     * 删除用户旧头像文件
     * 处理逻辑：
     * 1. 定位到 Nginx 静态目录下的 avatar 文件夹；
     * 2. 若目录不存在则直接返回；
     * 3. 尝试删除该用户所有可能扩展名的旧头像文件；
     * 4. 不会删除 default 目录中的默认头像。
     * 这样做的目的是：
     * 防止用户先上传 png，后上传 jpg，结果磁盘里残留多个旧头像文件。
     *
     * @param userId 用户唯一标识
     * @throws IOException 当文件删除过程中发生 IO 异常时抛出
     */
    private void deleteOldAvatarFiles(String userId) throws IOException {
        Path avatarDir = Paths.get(nginxUploadDir, "avatar");
        if (!Files.exists(avatarDir)) {
            return;
        }

        String[] extensions = {"png", "jpg", "jpeg", "webp"};
        for (String ext : extensions) {
            Path oldFile = avatarDir.resolve(userId + "." + ext);
            Files.deleteIfExists(oldFile);
        }
    }

    /**
     * 根据用户 ID 查询该用户保存的街道列表
     * 业务流程：
     * 1. 根据 userId 查询用户是否存在；
     * 2. 若用户不存在则抛出异常；
     * 3. 根据 creatorId 查询该用户创建的街道；
     * 4. 按更新时间倒序返回街道列表。
     *
     * @param userId 用户唯一标识
     * @return 用户街道列表
     * @throws RuntimeException 当用户不存在时抛出异常
     */
    public List<UserStreetListItemResponse> getUserStreetList(String userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        LambdaQueryWrapper<Street> wrapper = Wrappers.<Street>lambdaQuery()
                .eq(Street::getCreatorId, userId)
                .orderByDesc(Street::getUpdatedAt);

        List<Street> streetList = streetMapper.selectList(wrapper);

        return streetList.stream()
                .map(street -> new UserStreetListItemResponse(
                        street.getStreetId(),
                        street.getStreetName(),
                        street.getWidth(),
                        street.getCreatedAt(),
                        street.getUpdatedAt()
                ))
                .toList();
    }
}