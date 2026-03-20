package com.streetviz3d.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelPreviewDTO {
    private String modelId;
    private String modelName;
    private String displayName;
    private String modelPreview;
}