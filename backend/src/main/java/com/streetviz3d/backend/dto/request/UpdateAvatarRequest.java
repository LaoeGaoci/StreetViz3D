package com.streetviz3d.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAvatarRequest {

    @NotNull(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "头像地址不能为空")
    private String avatarUrl;
}