package com.streetviz3d.backend.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("streetviz3d.segment")
public class Segment {
    @TableId
    private String segmentId;

    private String streetId;
    private String externalUuid;
    private Integer sortIndex;
    private String segmentType;
    private Double width;
    private Double elevation;
    private Boolean slopeOn;
    private String slopeValues;
    private String modelId;
    private String variantData;
    private String createdAt;
    private String updatedAt;
}
