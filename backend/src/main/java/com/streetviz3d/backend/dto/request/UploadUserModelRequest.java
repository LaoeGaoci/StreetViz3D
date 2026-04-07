package com.streetviz3d.backend.dto.request;

import lombok.Data;

@Data
public class UploadUserModelRequest {
    private String userId;
    private String modelName;
    private String displayName;
    private String description;
    private String modelType;
    private String modelSubtype;
    private Boolean isPublic;
}
