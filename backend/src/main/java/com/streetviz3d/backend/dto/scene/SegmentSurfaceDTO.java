package com.streetviz3d.backend.dto.scene;

import lombok.Data;

@Data
public class SegmentSurfaceDTO {
    private SceneVector3DTO position;
    private double width;
    private double height;
    private double depth;
    private String color;
    private String materialKey;
}
