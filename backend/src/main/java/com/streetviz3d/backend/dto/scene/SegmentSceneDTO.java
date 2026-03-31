package com.streetviz3d.backend.dto.scene;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SegmentSceneDTO {
    private String segmentId;
    private Integer sortIndex;
    private String type;
    private JsonNode variantData;

    private double originalWidth;
    private double renderedWidth;

    private double startX;
    private double centerX;
    private double endX;

    private double elevation;
    private double elevationY;

    private SegmentSurfaceDTO surface;

    private List<SceneInstanceDTO> instances = new ArrayList<>();
}
