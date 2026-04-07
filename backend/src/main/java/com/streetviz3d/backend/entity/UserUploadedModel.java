package com.streetviz3d.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("streetviz3d.user_uploaded_model")
public class UserUploadedModel {

    @TableId(value = "upload_id", type = IdType.INPUT)
    private String uploadId;

    private String userId;
    private String modelName;
    private String displayName;
    private String description;
    private String modelType;
    private String modelSubtype;
    private String modelFormat;
    private String modelUrl;
    private String previewUrl;
    private String binUrl;
    private Boolean isPublic;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
