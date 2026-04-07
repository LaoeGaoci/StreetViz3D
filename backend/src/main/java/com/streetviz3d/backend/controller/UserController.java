package com.streetviz3d.backend.controller;

import com.streetviz3d.backend.dto.Result;
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
import com.streetviz3d.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterRequest request) {
        userService.register(request);
        return Result.success("注册成功", null);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return Result.success(userService.login(request));
    }

    /**
     * 用户注销
     */
    @DeleteMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.success("注销成功", null);
    }

    /**
     * 根据邮箱获取用户 ID
     */
    @GetMapping("/id")
    public Result<UserIdResponse> getUserId(@RequestParam String email) {
        return Result.success(userService.getUserIdByEmail(email));
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo(@RequestParam String userId) {
        return Result.success(userService.getUserInfo(userId));
    }

    /**
     * 上传用户头像
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAvatar(
            @RequestParam("userId") String userId,
            @RequestParam("file") MultipartFile file
    ) {
        String avatarUrl = userService.uploadAvatar(userId, file);
        return Result.success("头像上传成功", avatarUrl);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(request);
        return Result.success("密码修改成功", null);
    }

    /**
     * 获取用户街道列表
     */
    @GetMapping("/streetList")
    public Result<List<UserStreetListItemResponse>> getUserStreetList(@RequestParam String userId) {
        return Result.success(userService.getUserStreetList(userId));
    }

    /**
     * 第一步：创建用户模型草稿
     */
    @PostMapping("/model")
    public Result<CreateUserModelDraftResponse> createUserModelDraft(
            @RequestBody UploadUserModelRequest request
    ) {
        return Result.success(
                "用户模型草稿创建成功",
                userService.createUserModelDraft(request)
        );
    }

    /**
     * 第二步：上传或覆盖模型文件
     * 按头像上传风格，统一使用 RequestParam + multipart/form-data
     */
    @PostMapping(value = "/model/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserUploadedModelResponse> uploadUserModelFiles(
            @RequestParam("userId") String userId,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("gltfFile") MultipartFile gltfFile,
            @RequestParam(value = "binFile", required = false) MultipartFile binFile,
            @RequestParam(value = "textureFiles", required = false) List<MultipartFile> textureFiles,
            @RequestParam(value = "previewFile", required = false) MultipartFile previewFile
    ) {
        return Result.success(
                "用户模型文件上传成功",
                userService.uploadUserModelFiles(userId, uploadId, gltfFile, binFile, textureFiles, previewFile)
        );
    }

    /**
     * 删除用户模型（逻辑删除）
     */
    @DeleteMapping("/model")
    public Result<Void> deleteUserModel(
            @RequestParam String userId,
            @RequestParam String uploadId
    ) {
        userService.deleteUserModel(userId, uploadId);
        return Result.success("用户模型删除成功", null);
    }

    /**
     * 获取用户上传模型列表
     */
    @GetMapping("/model/list")
    public Result<List<UserUploadedModelResponse>> getUserUploadedModelList(
            @RequestParam String userId
    ) {
        return Result.success(userService.getUserUploadedModelList(userId));
    }

    /**
     * 修改用户模型公开状态
     */
    @PutMapping("/model/public")
    public Result<UserUploadedModelResponse> updateUserModelPublicStatus(
            @RequestBody UpdateUserModelPublicRequest request
    ) {
        return Result.success(
                "用户模型公开状态修改成功",
                userService.updateUserModelPublicStatus(request)
        );
    }

    /**
     * 单独修改用户模型预览图
     */
    @PutMapping(value = "/model/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserUploadedModelResponse> updateUserModelPreview(
            @RequestParam("userId") String userId,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("previewFile") MultipartFile previewFile
    ) {
        return Result.success(
                "用户模型预览图修改成功",
                userService.updateUserModelPreview(userId, uploadId, previewFile)
        );
    }
}