package com.streetviz3d.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.user-model-storage")
public class UserModelStorageProperties {

    /**
     * 本地存储根目录：
     * D:/nginx/nginx-1.26.2/StreetViz3D/auth/model
     */
    private String uploadDir;

    /**
     * 对外访问前缀：
     * http://localhost:65/auth/model
     */
    private String baseUrl;
}