package com.streetviz3d.backend.controller;

import com.streetviz3d.backend.dto.Result;
import com.streetviz3d.backend.dto.request.UpdateAvatarRequest;
import com.streetviz3d.backend.dto.request.UpdatePasswordRequest;
import com.streetviz3d.backend.dto.request.UserLoginRequest;
import com.streetviz3d.backend.dto.request.UserRegisterRequest;
import com.streetviz3d.backend.dto.response.LoginResponse;
import com.streetviz3d.backend.dto.response.UserIdResponse;
import com.streetviz3d.backend.dto.response.UserInfoResponse;
import com.streetviz3d.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterRequest request) {
        userService.register(request);
        return Result.success("注册成功", null);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return Result.success(userService.login(request));
    }

    @DeleteMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.success("注销成功", null);
    }

    @GetMapping("/id")
    public Result<UserIdResponse> getUserId(@RequestParam String email) {
        return Result.success(userService.getUserIdByEmail(email));
    }

    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo(@RequestParam String userId) {
        return Result.success(userService.getUserInfo(userId));
    }
    /**
     * 上传用户头像
     *
     * 接口说明：
     * 1. 接收前端上传的 userId 和头像文件；
     * 2. 调用业务层完成头像保存、旧头像删除、数据库路径更新；
     * 3. 返回头像完整访问地址。
     *
     * 请求方式：
     * POST /user/avatar
     *
     * 请求参数：
     * @param userId 用户唯一标识
     * @param file 用户上传的头像文件
     *
     * 返回结果：
     * - 成功：返回完整头像 URL
     * - 失败：抛出业务异常，由全局异常处理器统一处理
     *
     * @return 统一响应对象，data 中为完整头像访问地址
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(
            @RequestParam("userId") String userId,
            @RequestParam("file") MultipartFile file
    ) {
        String avatarUrl = userService.uploadAvatar(userId, file);
        return Result.success("头像上传成功", avatarUrl);
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(request);
        return Result.success("密码修改成功", null);
    }
}