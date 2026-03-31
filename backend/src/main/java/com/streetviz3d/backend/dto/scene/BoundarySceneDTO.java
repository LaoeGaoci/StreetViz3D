package com.streetviz3d.backend.dto.scene;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BoundarySceneDTO {
    private String boundaryId;
    private String side;
    private String type;
    private Integer floors;
    private double elevation;
    private JsonNode variantData;

    private double centerX;
    private double supportHeight;

    private SceneInstanceDTO supportSurface;

    private List<SceneInstanceDTO> instances = new ArrayList<>();
}
