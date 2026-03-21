package com.streetviz3d.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String userId;
    private String userName;
    private String email;
}