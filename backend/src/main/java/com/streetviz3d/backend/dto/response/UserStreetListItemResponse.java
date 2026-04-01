package com.streetviz3d.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserStreetListItemResponse {
    private String streetId;
    private String streetName;
    private Double width;
    private String createdAt;
    private String updatedAt;
}