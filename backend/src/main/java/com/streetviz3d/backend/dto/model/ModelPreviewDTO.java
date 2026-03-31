package com.streetviz3d.backend.dto.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelPreviewDTO {
    private String modelId;
    private String modelName;
    private String displayName;
    private String modelPreview;

    @Data
    public static class UpdatePasswordRequest {

        private UUID userId;

        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        private String newPassword;
    }
}