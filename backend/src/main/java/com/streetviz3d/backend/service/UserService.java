package com.streetviz3d.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.streetviz3d.backend.config.UserModelStorageProperties;
import com.streetviz3d.backend.dto.request.UpdatePasswordRequest;
import com.streetviz3d.backend.dto.request.UpdateUserModelPublicRequest;
import com.streetviz3d.backend.dto.request.UploadUserModelRequest;
import com.streetviz3d.backend.dto.request.UserLoginRequest;
import com.streetviz3d.backend.dto.request.UserRegisterRequest;
import com.streetviz3d.backend.dto.response.CreateUserModelDraftResponse;
import com.streetviz3d.backend.dto.response.LoginResponse;
import com.streetviz3d.backend.dto.response.UserIdResponse;
import com.streetviz3d.backend.dto.response.UserInfoResponse;
import com.streetviz3d.backend.dto.response.UserStreetListItemResponse;
import com.streetviz3d.backend.dto.response.UserUploadedModelResponse;
import com.streetviz3d.backend.entity.AppUser;
import com.streetviz3d.backend.entity.Street;
import com.streetviz3d.backend.entity.UserUploadedModel;
import com.streetviz3d.backend.mapper.AppUserMapper;
import com.streetviz3d.backend.mapper.StreetMapper;
import com.streetviz3d.backend.mapper.UserUploadedModelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserMapper appUserMapper;
    private final StreetMapper streetMapper;
    private final UserUploadedModelMapper userUploadedModelMapper;
    private final UserModelStorageProperties userModelStorageProperties;

    @Value("${app.nginx.base-url}")
    private String nginxBaseUrl;

    @Value("${app.nginx.upload-dir}")
    private String nginxUploadDir;

    /**
     * 数据库存储的默认头像相对路径。
     */
    private static final String DEFAULT_AVATAR_PATH = "default/default-avatar.png";

    /**
     * 允许的图片 MIME 类型。
     * 头像、预览图、贴图都复用这一套校验。
     */
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp"
    );

    /**
     * 允许的图片扩展名。
     */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            "png",
            "jpg",
            "jpeg",
            "webp"
    );

    /**
     * 用户注册。
     */
    public void register(UserRegisterRequest request) {
        boolean emailExists = appUserMapper.selectOne(
                Wrappers.<AppUser>lambdaQuery().eq(AppUser::getEmail, request.getEmail())
        ) != null;
        if (emailExists) {
            throw new RuntimeException("邮箱已被注册");
        }

        boolean userNameExists = appUserMapper.selectOne(
                Wrappers.<AppUser>lambdaQuery().eq(AppUser::getUserName, request.getUserName())
        ) != null;
        if (userNameExists) {
            throw new RuntimeException("用户名已存在");
        }

        OffsetDateTime now = OffsetDateTime.now();

        AppUser user = new AppUser();
        user.setUserId(UUID.randomUUID().toString());
        user.setUserName(request.getUserName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword());
        user.setAvatarUrl(DEFAULT_AVATAR_PATH);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        appUserMapper.insert(user);
    }

    /**
     * 用户登录。
     */
    public LoginResponse login(UserLoginRequest request) {
        AppUser user = appUserMapper.selectOne(
                Wrappers.<AppUser>lambdaQuery().eq(AppUser::getEmail, request.getEmail())
        );
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
     * 用户退出登录。
     * 当前未接入 token / session，保持幂等成功即可。
     */
    public void logout() {
        // 当前没有 token / session 体系，直接返回成功
    }

    /**
     * 根据邮箱获取用户 ID。
     */
    public UserIdResponse getUserIdByEmail(String email) {
        AppUser user = appUserMapper.selectOne(
                Wrappers.<AppUser>lambdaQuery().eq(AppUser::getEmail, email)
        );
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return new UserIdResponse(user.getUserId());
    }

    /**
     * 获取用户信息。
     */
    public UserInfoResponse getUserInfo(String userId) {
        AppUser user = getUserByIdOrThrow(userId);

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
     * 上传用户头像。
     */
    public String uploadAvatar(String userId, MultipartFile file) {
        AppUser user = getUserByIdOrThrow(userId);
        validateRequiredFile(file, "头像文件不能为空");

        String extension = resolveImageExtension(file, "仅支持 png、jpg、jpeg、webp 图片格式");
        String relativePath = "avatar/" + userId + "." + extension;
        Path avatarDir = Paths.get(nginxUploadDir, "avatar");
        Path targetPath = avatarDir.resolve(userId + "." + extension);

        try {
            Files.createDirectories(avatarDir);
            deleteFilesByBaseName(avatarDir, userId, ALLOWED_IMAGE_EXTENSIONS.toArray(new String[0]));
            copyMultipartFile(file, targetPath);
        } catch (IOException e) {
            throw new RuntimeException("头像文件保存失败", e);
        }

        user.setAvatarUrl(relativePath);
        user.setUpdatedAt(OffsetDateTime.now());
        appUserMapper.updateById(user);

        return buildAvatarUrl(relativePath);
    }

    /**
     * 修改用户密码。
     */
    public void updatePassword(UpdatePasswordRequest request) {
        AppUser user = getUserByIdOrThrow(request.getUserId());

        if (!user.getPasswordHash().equals(request.getOldPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        user.setPasswordHash(request.getNewPassword());
        user.setUpdatedAt(OffsetDateTime.now());
        appUserMapper.updateById(user);
    }

    /**
     * 获取用户创建的街道列表。
     */
    public List<UserStreetListItemResponse> getUserStreetList(String userId) {
        getUserByIdOrThrow(userId);

        List<Street> streetList = streetMapper.selectList(
                Wrappers.<Street>lambdaQuery()
                        .eq(Street::getCreatorId, userId)
                        .orderByDesc(Street::getUpdatedAt)
        );

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

    /**
     * 第一步：创建用户模型草稿记录。
     */
    public CreateUserModelDraftResponse createUserModelDraft(UploadUserModelRequest request) {
        if (request == null) {
            throw new RuntimeException("模型元数据不能为空");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new RuntimeException("userId不能为空");
        }
        if (!StringUtils.hasText(request.getModelName())) {
            throw new RuntimeException("modelName不能为空");
        }

        getUserByIdOrThrow(request.getUserId());

        String uploadId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        UserUploadedModel entity = new UserUploadedModel();
        entity.setUploadId(uploadId);
        entity.setUserId(request.getUserId());
        entity.setModelName(request.getModelName());
        entity.setDisplayName(request.getDisplayName());
        entity.setDescription(request.getDescription());
        entity.setModelType(request.getModelType());
        entity.setModelSubtype(request.getModelSubtype());
        entity.setModelFormat("gltf");
        entity.setModelUrl(null);
        entity.setPreviewUrl(null);
        entity.setBinUrl(null);
        entity.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        userUploadedModelMapper.insert(entity);

        return new CreateUserModelDraftResponse(uploadId);
    }

    /**
     * 第二步：上传或覆盖模型文件。
     *
     * 新规则：
     * 1. gltf / bin / 所有贴图 统一存放到 auth/model 目录下；
     * 2. 预览图统一存放到 auth/model/preview 目录下；
     * 3. 为避免重名覆盖，模型文件与贴图统一追加 uploadId_ 前缀；
     * 4. 预览图命名统一为 Preview_模型名称.扩展名；
     * 5. modelUrl / binUrl / previewUrl 保存完整可访问 URL。
     */
    public UserUploadedModelResponse uploadUserModelFiles(
            String userId,
            String uploadId,
            MultipartFile gltfFile,
            MultipartFile binFile,
            List<MultipartFile> textureFiles,
            MultipartFile previewFile
    ) {
        if (!StringUtils.hasText(userId)) {
            throw new RuntimeException("userId不能为空");
        }
        if (!StringUtils.hasText(uploadId)) {
            throw new RuntimeException("uploadId不能为空");
        }

        validateRequiredFile(gltfFile, "gltf文件不能为空");
        validateFileExtension(gltfFile, "gltf", "主模型文件必须是 .gltf 格式");

        if (binFile != null && !binFile.isEmpty()) {
            validateFileExtension(binFile, "bin", "二进制文件必须是 .bin 格式");
        }

        validateImageFiles(textureFiles, "贴图文件仅支持 png、jpg、jpeg、webp");

        if (previewFile != null && !previewFile.isEmpty()) {
            resolveImageExtension(previewFile, "预览图仅支持 png、jpg、jpeg、webp");
        }

        UserUploadedModel model = getOwnedUserModel(userId, uploadId);

        Path modelDir = buildModelDir();
        Path previewDir = buildPreviewDir();

        try {
            Files.createDirectories(modelDir);
            Files.createDirectories(previewDir);

            // 1. 保存 gltf 到 /auth/model
            String originalGltfName = sanitizeFileName(gltfFile.getOriginalFilename());
            if (!originalGltfName.toLowerCase(Locale.ROOT).endsWith(".gltf")) {
                throw new RuntimeException("主模型文件必须是 .gltf 格式");
            }
            String gltfStoredName = addUploadIdPrefix(uploadId, originalGltfName);
            deleteFileIfExists(modelDir.resolve(gltfStoredName));
            copyMultipartFile(gltfFile, modelDir.resolve(gltfStoredName));
            String modelUrl = buildModelFileUrl(gltfStoredName);

            // 2. 保存 bin 到 /auth/model
            String binUrl = model.getBinUrl();
            if (binFile != null && !binFile.isEmpty()) {
                String originalBinName = sanitizeFileName(binFile.getOriginalFilename());
                if (!originalBinName.toLowerCase(Locale.ROOT).endsWith(".bin")) {
                    throw new RuntimeException("二进制文件必须是 .bin 格式");
                }
                String binStoredName = addUploadIdPrefix(uploadId, originalBinName);
                deleteFileIfExists(modelDir.resolve(binStoredName));
                copyMultipartFile(binFile, modelDir.resolve(binStoredName));
                binUrl = buildModelFileUrl(binStoredName);
            }

            // 3. 保存贴图到 /auth/model
            if (textureFiles != null) {
                for (MultipartFile textureFile : textureFiles) {
                    if (textureFile == null || textureFile.isEmpty()) {
                        continue;
                    }
                    String textureOriginalName = sanitizeFileName(textureFile.getOriginalFilename());
                    String textureStoredName = addUploadIdPrefix(uploadId, textureOriginalName);
                    deleteFileIfExists(modelDir.resolve(textureStoredName));
                    copyMultipartFile(textureFile, modelDir.resolve(textureStoredName));
                }
            }

            // 4. 保存预览图到 /auth/model/preview
            String previewUrl = model.getPreviewUrl();
            if (previewFile != null && !previewFile.isEmpty()) {
                String previewExt = resolveImageExtension(previewFile, "预览图仅支持 png、jpg、jpeg、webp");
                String previewFileName = buildPreviewFileName(model.getModelName(), previewExt);

                deleteFilesByPrefix(
                        previewDir,
                        "Preview_" + sanitizeModelNameForPreview(model.getModelName()) + "."
                );
                copyMultipartFile(previewFile, previewDir.resolve(previewFileName));
                previewUrl = buildPreviewFileUrl(previewFileName);
            }

            model.setModelUrl(modelUrl);
            model.setBinUrl(binUrl);
            model.setPreviewUrl(previewUrl);
            model.setUpdatedAt(OffsetDateTime.now());

            userUploadedModelMapper.updateById(model);
            return toUserUploadedModelResponse(model);
        } catch (IOException e) {
            throw new RuntimeException("用户模型文件上传失败", e);
        }
    }

    /**
     * 删除用户模型。
     * 当前采用物理删除数据库记录 + 删除相关磁盘文件。
     */
    public void deleteUserModel(String userId, String uploadId) {
        UserUploadedModel model = getOwnedUserModel(userId, uploadId);

        try {
            deleteUserModelPhysicalFiles(model);
        } catch (IOException e) {
            throw new RuntimeException("删除用户模型文件失败", e);
        }

        userUploadedModelMapper.deleteById(model.getUploadId());
    }

    /**
     * 获取用户上传的模型列表。
     */
    public List<UserUploadedModelResponse> getUserUploadedModelList(String userId) {
        getUserByIdOrThrow(userId);

        List<UserUploadedModel> modelList = userUploadedModelMapper.selectList(
                Wrappers.<UserUploadedModel>lambdaQuery()
                        .eq(UserUploadedModel::getUserId, userId)
                        .orderByDesc(UserUploadedModel::getUpdatedAt)
        );

        return modelList.stream()
                .map(this::toUserUploadedModelResponse)
                .toList();
    }

    /**
     * 修改用户模型公开状态。
     */
    public UserUploadedModelResponse updateUserModelPublicStatus(UpdateUserModelPublicRequest request) {
        if (request == null || !StringUtils.hasText(request.getUserId())) {
            throw new RuntimeException("userId不能为空");
        }
        if (!StringUtils.hasText(request.getUploadId())) {
            throw new RuntimeException("uploadId不能为空");
        }
        if (request.getIsPublic() == null) {
            throw new RuntimeException("isPublic不能为空");
        }

        UserUploadedModel model = getOwnedUserModel(request.getUserId(), request.getUploadId());
        model.setIsPublic(request.getIsPublic());
        model.setUpdatedAt(OffsetDateTime.now());
        userUploadedModelMapper.updateById(model);

        return toUserUploadedModelResponse(model);
    }

    /**
     * 单独修改用户模型预览图。
     */
    public UserUploadedModelResponse updateUserModelPreview(
            String userId,
            String uploadId,
            MultipartFile previewFile
    ) {
        if (!StringUtils.hasText(userId)) {
            throw new RuntimeException("userId不能为空");
        }
        if (!StringUtils.hasText(uploadId)) {
            throw new RuntimeException("uploadId不能为空");
        }
        validateRequiredFile(previewFile, "预览图不能为空");

        String extension = resolveImageExtension(previewFile, "预览图仅支持 png、jpg、jpeg、webp");
        UserUploadedModel model = getOwnedUserModel(userId, uploadId);

        Path previewDir = buildPreviewDir();
        String previewFileName = buildPreviewFileName(model.getModelName(), extension);

        try {
            Files.createDirectories(previewDir);
            deleteFilesByPrefix(
                    previewDir,
                    "Preview_" + sanitizeModelNameForPreview(model.getModelName()) + "."
            );
            copyMultipartFile(previewFile, previewDir.resolve(previewFileName));
        } catch (IOException e) {
            throw new RuntimeException("预览图保存失败", e);
        }

        model.setPreviewUrl(buildPreviewFileUrl(previewFileName));
        model.setUpdatedAt(OffsetDateTime.now());
        userUploadedModelMapper.updateById(model);

        return toUserUploadedModelResponse(model);
    }

    /**
     * 根据用户 ID 查询用户，不存在则抛异常。
     */
    private AppUser getUserByIdOrThrow(String userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    /**
     * 获取当前用户拥有的模型记录，不存在则抛异常。
     */
    private UserUploadedModel getOwnedUserModel(String userId, String uploadId) {
        LambdaQueryWrapper<UserUploadedModel> wrapper = Wrappers.<UserUploadedModel>lambdaQuery()
                .eq(UserUploadedModel::getUploadId, uploadId)
                .eq(UserUploadedModel::getUserId, userId);

        UserUploadedModel model = userUploadedModelMapper.selectOne(wrapper);
        if (model == null) {
            throw new RuntimeException("用户模型不存在");
        }
        return model;
    }

    /**
     * 将用户模型实体转为响应对象。
     */
    private UserUploadedModelResponse toUserUploadedModelResponse(UserUploadedModel entity) {
        UserUploadedModelResponse response = new UserUploadedModelResponse();
        response.setUploadId(entity.getUploadId());
        response.setUserId(entity.getUserId());
        response.setModelName(entity.getModelName());
        response.setDisplayName(entity.getDisplayName());
        response.setDescription(entity.getDescription());
        response.setModelType(entity.getModelType());
        response.setModelSubtype(entity.getModelSubtype());
        response.setModelFormat(entity.getModelFormat());
        response.setModelUrl(entity.getModelUrl());
        response.setPreviewUrl(entity.getPreviewUrl());
        response.setBinUrl(entity.getBinUrl());
        response.setIsPublic(entity.getIsPublic());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    /**
     * 校验文件必须存在。
     */
    private void validateRequiredFile(MultipartFile file, String message) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException(message);
        }
    }

    /**
     * 校验文件扩展名。
     */
    private void validateFileExtension(MultipartFile file, String requiredExt, String errorMessage) {
        String ext = getExtension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!requiredExt.equals(ext)) {
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * 校验图片文件列表。
     */
    private void validateImageFiles(List<MultipartFile> files, String errorMessage) {
        if (files == null) {
            return;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            resolveImageExtension(file, errorMessage);
        }
    }

    /**
     * 解析图片扩展名。
     * 优先读文件名扩展名，若不可靠则退化为 contentType 推断。
     */
    private String resolveImageExtension(MultipartFile file, String errorMessage) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            throw new RuntimeException(errorMessage);
        }

        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new RuntimeException(errorMessage);
        }

        String extFromFilename = getExtension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (ALLOWED_IMAGE_EXTENSIONS.contains(extFromFilename)) {
            return extFromFilename;
        }

        return switch (normalizedContentType) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new RuntimeException(errorMessage);
        };
    }

    /**
     * 复制 MultipartFile 到目标路径。
     */
    private void copyMultipartFile(MultipartFile file, Path targetPath) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 根据基础文件名批量删除同名不同扩展名的文件。
     * 例如：preview.png / preview.jpg / preview.webp。
     */
    private void deleteFilesByBaseName(Path dir, String baseName, String... extensions) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        for (String ext : extensions) {
            Files.deleteIfExists(dir.resolve(baseName + "." + ext));
        }
    }

    /**
     * 按文件名前缀删除文件。
     * 例如删除 Preview_xxx.png / Preview_xxx.jpg / Preview_xxx.webp。
     */
    private void deleteFilesByPrefix(Path dir, String prefix) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                if (fileName.startsWith(prefix)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    /**
     * 删除单个文件（若存在）。
     */
    private void deleteFileIfExists(Path filePath) throws IOException {
        Files.deleteIfExists(filePath);
    }

    /**
     * 删除整个目录（若存在）。
     */
    private void deleteDirectoryIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path currentDir, IOException exc) throws IOException {
                Files.deleteIfExists(currentDir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 构建模型文件目录：
     * D:\nginx\nginx-1.26.2\StreetViz3D\auth\model\
     */
    private Path buildModelDir() {
        return Paths.get(nginxUploadDir, "model");
    }

    /**
     * 构建预览图目录：
     * D:\nginx\nginx-1.26.2\StreetViz3D\auth\model\preview\
     */
    private Path buildPreviewDir() {
        return Paths.get(nginxUploadDir, "model", "preview");
    }

    /**
     * 构建模型文件完整访问 URL。
     */
    private String buildModelFileUrl(String fileName) {
        return nginxBaseUrl + "/auth/model/" + fileName;
    }

    /**
     * 构建预览图完整访问 URL。
     */
    private String buildPreviewFileUrl(String fileName) {
        return nginxBaseUrl + "/auth/model/preview/" + fileName;
    }

    /**
     * 构建预览图文件名：Preview_模型名称.扩展名
     */
    private String buildPreviewFileName(String modelName, String extension) {
        String safeModelName = sanitizeModelNameForPreview(modelName);
        return "Preview_" + safeModelName + "." + extension;
    }

    /**
     * 给模型资源文件增加 uploadId 前缀，避免重名覆盖。
     * 例如：
     * house.gltf -> 123abc_house.gltf
     */
    private String addUploadIdPrefix(String uploadId, String fileName) {
        return uploadId + "_" + fileName;
    }

    /**
     * 删除该模型物理文件。
     * 这里只删除当前 uploadId 对应的资源，不动别的模型。
     */
    private void deleteUserModelPhysicalFiles(UserUploadedModel model) throws IOException {
        Path modelDir = buildModelDir();
        Path previewDir = buildPreviewDir();

        if (Files.exists(modelDir) && Files.isDirectory(modelDir)) {
            String uploadPrefix = model.getUploadId() + "_";

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelDir)) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }

                    String fileName = path.getFileName().toString();
                    if (fileName.startsWith(uploadPrefix)) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }

        if (Files.exists(previewDir) && Files.isDirectory(previewDir)) {
            String previewPrefix = "Preview_" + sanitizeModelNameForPreview(model.getModelName()) + ".";

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(previewDir)) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }

                    String fileName = path.getFileName().toString();
                    if (fileName.startsWith(previewPrefix)) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }

    /**
     * 构建头像完整访问地址。
     */
    private String buildAvatarUrl(String avatarPath) {
        String normalizedPath = normalizeAvatarPath(avatarPath);
        return nginxBaseUrl + "/auth/" + normalizedPath;
    }

    /**
     * 标准化头像相对路径。
     */
    private String normalizeAvatarPath(String avatarPath) {
        if (!StringUtils.hasText(avatarPath)) {
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
     * 从文件名提取扩展名。
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
     * 清洗普通文件名，避免路径穿越与特殊字符问题。
     */
    private String sanitizeFileName(String filename) {
        if (!StringUtils.hasText(filename)) {
            return UUID.randomUUID().toString();
        }

        return filename.replace("\\", "/")
                .replaceAll(".*/", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * 清洗用于预览图命名的模型名称。
     * 保留中文，空格改下划线，过滤 Windows 非法字符。
     */
    private String sanitizeModelNameForPreview(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            return "UnknownModel";
        }

        return modelName.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}