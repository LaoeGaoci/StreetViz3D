package com.streetviz3d.backend.dto.scene;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StreetSceneDTO {
    private String streetId;
    private String streetName;

    private double streetWidth;
    private double roadLength;
    private double widthScale;

    private SceneBaseDTO base;
    private SceneStyleDTO style;

    private List<SegmentSceneDTO> segments = new ArrayList<>();

    private BoundarySceneDTO leftBoundary;
    private BoundarySceneDTO rightBoundary;
}
