package com.streetviz3d.backend.dto.street;

import com.fasterxml.jackson.databind.JsonNode;
import com.streetviz3d.backend.dto.model.ModelAssetDTO;
import lombok.Data;

@Data
public class BoundaryPreviewDTO {
    private String boundaryId;
    private String side;
    private String type;
    private Integer floors;
    private Double elevation;
    private JsonNode variantData;
    private ModelAssetDTO model;
}
