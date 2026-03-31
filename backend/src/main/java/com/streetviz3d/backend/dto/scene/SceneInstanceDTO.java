package com.streetviz3d.backend.dto.scene;

import lombok.Data;

@Data
public class SceneInstanceDTO {

    private String kind;

    private String semanticType;

    private String modelId;
    private String modelUrl;
    private String displayName;

    private SceneVector3DTO position;
    private SceneVector3DTO rotation;
    private SceneVector3DTO scale;

    private Double width;
    private Double height;
    private Double depth;
    private String color;
}
