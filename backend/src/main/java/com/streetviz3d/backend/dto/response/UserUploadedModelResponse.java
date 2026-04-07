package com.streetviz3d.backend.dto.response;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class UserUploadedModelResponse {
    private String uploadId;
    private String userId;
    private String modelName;
    private String displayName;
    private String description;
    private String modelType;
    private String modelSubtype;
    private String modelFormat;
    private String modelUrl;
    private String previewUrl;
    private String binUrl;
    private String extraFiles;
    private Long fileSize;
    private String status;
    private Boolean isPublic;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}