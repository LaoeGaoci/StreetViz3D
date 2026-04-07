package com.streetviz3d.backend.dto.request;

import lombok.Data;

@Data
public class UpdateUserModelPublicRequest {
    private String userId;
    private String uploadId;
    private Boolean isPublic;
}