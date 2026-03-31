package com.streetviz3d.backend.dto.response;


import com.streetviz3d.backend.dto.street.BoundaryPreviewDTO;
import com.streetviz3d.backend.dto.street.SegmentPreviewDTO;
import lombok.Data;

import java.util.List;

@Data
public class StreetPreviewResponse {
    private String streetId;
    private String streetName;
    private String creatorId;
    private Integer namespacedId;
    private Integer unit;
    private Integer schemaVersion;
    private Double width;
    private String skybox;
    private String weather;
    private String location;
    private Integer editCount;

    private List<SegmentPreviewDTO> segments;
    private BoundaryMap boundaries;

    @Data
    public static class BoundaryMap {
        private BoundaryPreviewDTO left;
        private BoundaryPreviewDTO right;
    }
}