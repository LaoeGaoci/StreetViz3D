package com.streetviz3d.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.streetviz3d.backend.dto.request.UpdateAvatarRequest;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserMapper appUserMapper;

    @Value("${app.nginx.base-url}")
    private String nginxBaseUrl;

    // 数据库存储的默认头像相对路径
    private static final String DEFAULT_AVATAR_PATH = "default/default-avatar.png";

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
        user.setAvatarUrl(DEFAULT_AVATAR_PATH); // 这里只存相对路径
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        appUserMapper.insert(user);
    }

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

    public void logout() {
        // 当前没有 token / session 体系，这里直接幂等返回成功
    }

    public UserIdResponse getUserIdByEmail(String email) {
        LambdaQueryWrapper<AppUser> wrapper = Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getEmail, email);

        AppUser user = appUserMapper.selectOne(wrapper);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return new UserIdResponse(user.getUserId());
    }

    public UserInfoResponse getUserInfo(UUID userId) {
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

    public void updateAvatar(UpdateAvatarRequest request) {
        AppUser user = appUserMapper.selectById(request.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 这里要求传入相对路径，例如：
        // avatars/user-001.png
        // default/default-avatar.png
        user.setAvatarUrl(normalizeAvatarPath(request.getAvatarUrl()));
        user.setUpdatedAt(OffsetDateTime.now());
        appUserMapper.updateById(user);
    }

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

    private String buildAvatarUrl(String avatarPath) {
        String normalizedPath = normalizeAvatarPath(avatarPath);
        return nginxBaseUrl + "/auth/" + normalizedPath;
    }

    private String normalizeAvatarPath(String avatarPath) {
        if (avatarPath == null || avatarPath.isBlank()) {
            return DEFAULT_AVATAR_PATH;
        }

        String normalized = avatarPath.trim().replace("\\", "/");

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized.isBlank() ? DEFAULT_AVATAR_PATH : normalized;
    }
}