package com.streetviz3d.backend.dto.scene;

import lombok.Data;

@Data
public class SceneBaseDTO {
    private double width;
    private double height;
    private double depth;
    private SceneVector3DTO position;
    private String color;
}
