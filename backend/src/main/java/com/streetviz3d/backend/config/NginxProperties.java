package com.streetviz3d.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.nginx")
public class NginxProperties {
    private String baseUrl;
}