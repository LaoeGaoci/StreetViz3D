package com.streetviz3d.backend.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("streetviz3d.street")
public class Street {
    @TableId
    private String streetId;

    private String externalUuid;
    private String streetName;
    private Integer namespacedId;
    private String creatorId;
    private String originalStreetId;
    private Integer unit;
    private Integer schemaVersion;
    private Double width;
    private String skybox;
    private String weather;
    private String location;
    private Boolean isUserUpdated;
    private Integer editCount;
    private String rawJson;
    private String createdAt;
    private String updatedAt;
}
