package com.streetviz3d.backend.dto.scene;

import lombok.Data;

@Data
public class SceneStyleDTO {
    private String skyColor;
    private String ambientLightColor;
    private Double ambientLightIntensity;
    private String directionalLightColor;
    private Double directionalLightIntensity;
}
