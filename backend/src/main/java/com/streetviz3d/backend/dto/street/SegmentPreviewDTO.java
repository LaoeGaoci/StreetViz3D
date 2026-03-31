package com.streetviz3d.backend.dto.street;

import com.fasterxml.jackson.databind.JsonNode;
import com.streetviz3d.backend.dto.model.ModelAssetDTO;
import lombok.Data;

@Data
public class SegmentPreviewDTO {
    private String segmentId;
    private Integer sortIndex;
    private String type;
    private Double width;
    private Double elevation;
    private Boolean slopeOn;
    private JsonNode slopeValues;
    private JsonNode variantData;
    private ModelAssetDTO model;
}