package com.streetviz3d.backend.dto.model;

import lombok.Data;

@Data
public class ModelAssetDTO {
    private String modelId;
    private String modelName;
    private String modelType;
    private String modelSubtype;
    private String displayName;
    private String modelUrl;
    private String previewUrl;
}
