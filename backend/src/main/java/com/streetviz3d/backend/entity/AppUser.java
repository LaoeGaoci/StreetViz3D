package com.streetviz3d.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("streetviz3d.app_user")
public class AppUser {

    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;

    private String userName;

    private String avatarUrl;

    private String email;

    private String passwordHash;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}