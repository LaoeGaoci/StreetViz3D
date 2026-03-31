package com.streetviz3d.backend.entity;


import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("streetviz3d.boundary")
public class Boundary {
    @TableId
    private String boundaryId;

    private String streetId;
    private String externalUuid;
    private String side;
    private String boundaryType;
    private Integer floors;
    private Double elevation;
    private String modelId;
    private String variantData;
    private String createdAt;
    private String updatedAt;
}