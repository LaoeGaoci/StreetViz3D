package com.streetviz3d.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StreetmixUrlRequest {

    @NotBlank(message = "streetmixUrl 不能为空")
    private String streetmixUrl;
}