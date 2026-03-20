package com.streetviz3d.backend.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("streetviz3d.model_asset")
public class ModelAsset {

    @TableId
    private String modelId;

    private String modelName;

    private String displayName;

    private String modelType;

    private String modelSubtype;

    private String modelUrl;

    private String modelPreviewUrl;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
