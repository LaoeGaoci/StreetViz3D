package com.streetviz3d.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class UserInfoResponse {
    private String userId;
    private String userName;
    private String avatarUrl;
    private String email;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}