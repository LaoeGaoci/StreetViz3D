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
    public Result<UserInfoResponse> getUserInfo(@RequestParam UUID userId) {
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/avatar")
    public Result<Void> updateAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        userService.updateAvatar(request);
        return Result.success("头像修改成功", null);
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(request);
        return Result.success("密码修改成功", null);
    }
}