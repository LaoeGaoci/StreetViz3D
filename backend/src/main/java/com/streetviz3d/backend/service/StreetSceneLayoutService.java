package com.streetviz3d.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.streetviz3d.backend.dto.response.StreetPreviewResponse;
import com.streetviz3d.backend.dto.scene.*;
import com.streetviz3d.backend.dto.street.BoundaryPreviewDTO;
import com.streetviz3d.backend.dto.street.SegmentPreviewDTO;
import com.streetviz3d.backend.entity.LayoutGA;
import com.streetviz3d.backend.entity.ModelAsset;
import com.streetviz3d.backend.mapper.ModelAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StreetSceneLayoutService {

    private static final double DEFAULT_ROAD_LENGTH = 80.0;
    private static final double DEFAULT_WIDTH_SCALE = 1.5;
    private static final double DEFAULT_BASE_HEIGHT = 3.0;

    private final StreetPreviewService streetPreviewService;
    private final ModelAssetMapper modelAssetMapper;

    public StreetSceneDTO buildStreetScene(String streetId) {
        StreetPreviewResponse preview = streetPreviewService.getStreetPreview(streetId);
        return buildStreetScene(preview);
    }

    public StreetSceneDTO buildStreetScene(StreetPreviewResponse preview) {
        StreetSceneDTO scene = new StreetSceneDTO();
        scene.setStreetId(preview.getStreetId());
        scene.setStreetName(preview.getStreetName());
        scene.setStreetWidth(preview.getWidth());
        scene.setRoadLength(DEFAULT_ROAD_LENGTH);
        scene.setWidthScale(DEFAULT_WIDTH_SCALE);
        scene.setStyle(buildSceneStyle(preview.getSkybox()));

        List<SegmentSceneDTO> segmentScenes = buildSegmentScenes(
                preview.getSegments(),
                scene.getRoadLength(),
                scene.getWidthScale()
        );
        scene.setSegments(segmentScenes);

        double totalRenderedRoadWidth = segmentScenes.stream()
                .mapToDouble(SegmentSceneDTO::getRenderedWidth)
                .sum();

        BoundarySceneDTO leftBoundary = buildBoundaryScene(
                preview.getBoundaries() != null ? preview.getBoundaries().getLeft() : null,
                "left",
                totalRenderedRoadWidth,
                scene.getRoadLength()
        );

        BoundarySceneDTO rightBoundary = buildBoundaryScene(
                preview.getBoundaries() != null ? preview.getBoundaries().getRight() : null,
                "right",
                totalRenderedRoadWidth,
                scene.getRoadLength()
        );

        scene.setLeftBoundary(leftBoundary);
        scene.setRightBoundary(rightBoundary);
        scene.setBase(buildSceneBase(totalRenderedRoadWidth, scene.getRoadLength(), leftBoundary, rightBoundary));

        return scene;
    }

    private SceneStyleDTO buildSceneStyle(String skybox) {
        SceneStyleDTO style = new SceneStyleDTO();
        style.setSkyColor(resolveSkyColor(skybox));
        style.setAmbientLightColor("#ffffff");
        style.setAmbientLightIntensity(1.0);
        style.setDirectionalLightColor("#ffffff");
        style.setDirectionalLightIntensity(1.05);
        return style;
    }

    private List<SegmentSceneDTO> buildSegmentScenes(
            List<SegmentPreviewDTO> segments,
            double roadLength,
            double widthScale
    ) {
        List<SegmentSceneDTO> results = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return results;
        }

        double totalRenderedWidth = segments.stream()
                .mapToDouble(segment -> safeWidth(segment.getWidth()) * widthScale)
                .sum();

        double cursor = -totalRenderedWidth / 2.0;

        for (SegmentPreviewDTO segment : segments) {
            double renderedWidth = safeWidth(segment.getWidth()) * widthScale;
            double startX = cursor;
            double centerX = startX + renderedWidth / 2.0;
            double endX = startX + renderedWidth;
            cursor = endX;

            SegmentSceneDTO dto = new SegmentSceneDTO();
            dto.setSegmentId(segment.getSegmentId());
            dto.setSortIndex(segment.getSortIndex());
            dto.setType(segment.getType());
            dto.setVariantData(segment.getVariantData());

            dto.setOriginalWidth(safeWidth(segment.getWidth()));
            dto.setRenderedWidth(renderedWidth);
            dto.setStartX(startX);
            dto.setCenterX(centerX);
            dto.setEndX(endX);

            dto.setElevation(safeElevation(segment.getElevation()));
            dto.setElevationY(safeElevation(segment.getElevation()));

            dto.setSurface(buildSegmentSurface(segment, centerX, renderedWidth, roadLength));
            dto.setInstances(buildSegmentInstances(segment, centerX, roadLength));

            results.add(dto);
        }

        return results;
    }

    private SegmentSurfaceDTO buildSegmentSurface(
            SegmentPreviewDTO segment,
            double centerX,
            double renderedWidth,
            double roadLength
    ) {
        double elevation = safeElevation(segment.getElevation());

        SegmentSurfaceDTO surface = new SegmentSurfaceDTO();
        surface.setPosition(new SceneVector3DTO(centerX, 0.05 + elevation / 2.0, 0));
        surface.setWidth(renderedWidth);
        surface.setHeight(0.1 + elevation);
        surface.setDepth(roadLength);
        surface.setColor(resolveSegmentColor(segment.getType(), segment.getVariantData()));
        surface.setMaterialKey(resolveSegmentMaterialKey(segment.getType(), segment.getVariantData()));

        return surface;
    }

    /**
     * 改动点：
     * 1. 删除“先额外添加一个中心主模型实例”的逻辑
     * 2. 所有实例都由规则生成
     * 3. 规则生成时，若 segment 绑定了 model，则每个实例都复用同一份模型信息
     */
    private List<SceneInstanceDTO> buildSegmentInstances(
            SegmentPreviewDTO segment,
            double centerX,
            double roadLength
    ) {
        List<SceneInstanceDTO> instances = new ArrayList<>();
        appendVariantDrivenInstances(instances, segment, centerX, roadLength);
        return instances;
    }

    /**
     * 统一创建 segment 实例：
     * 如果 segment 本身绑定了 model，则每个实例都自动继承同一个 modelId/modelUrl/displayName
     */
    private SceneInstanceDTO createSegmentInstance(
            SegmentPreviewDTO segment,
            String kind,
            String semanticType,
            double x,
            double y,
            double z,
            double rotationY
    ) {
        SceneInstanceDTO instance = new SceneInstanceDTO();
        instance.setKind(kind);
        instance.setSemanticType(semanticType);
        instance.setPosition(new SceneVector3DTO(x, y, z));
        instance.setRotation(new SceneVector3DTO(0, rotationY, 0));
        instance.setScale(new SceneVector3DTO(1, 1, 1));

        if (segment.getModel() != null) {
            instance.setModelId(segment.getModel().getModelId());
            instance.setModelUrl(segment.getModel().getModelUrl());
            instance.setDisplayName(segment.getModel().getDisplayName());
        }

        return instance;
    }

    private void appendVariantDrivenInstances(
            List<SceneInstanceDTO> instances,
            SegmentPreviewDTO segment,
            double centerX,
            double roadLength
    ) {
        String type = segment.getType();
        JsonNode variantData = segment.getVariantData();
        double elevation = safeElevation(segment.getElevation());

        double[] defaultZ;
        int count;
        double offsetX = 0.0;
        double baseY;

        switch (type) {
            case "drive-lane":
                count = 4;
                defaultZ = new double[]{-16.0, 14.0};
                break;
            case "bus-lane":
                count = 1;
                defaultZ = new double[]{0.0};
                break;
            case "parking-lane":
                count = 4;
                defaultZ = new double[]{-22.0, -8.0, 8.0, 22.0};
                offsetX = "left".equalsIgnoreCase(readText(variantData, "placementSide")) ? -0.2 : 0.2;
                break;
            case "flex-zone":
                count = 5;
                offsetX = "left".equalsIgnoreCase(readText(variantData, "placementSide")) ? -0.15 : 0.15;
                break;
            case "temporary":
                count = (int)Math.floor(roadLength / 10.0);
                break;
            case "sidewalk-tree":
                count = (int)Math.floor(roadLength / 8.0);
                break;
            case "sidewalk":
                // 根据 pedestrianDensity 决定数量
                String density = readText(variantData, "pedestrianDensity");
                if ("empty".equalsIgnoreCase(density)) return;
                switch(density.toLowerCase()) {
                    case "dense": defaultZ = new double[]{-20, -12, -4, 4, 12, 20}; break;
                    case "normal": defaultZ = new double[]{-14, -2, 10}; break;
                    case "sparse": defaultZ = new double[]{0}; break;
                    default: defaultZ = new double[]{}; break;
                }
                count = defaultZ.length;
                break;
            default: return;
        }

        // 使用 GA 优化 Z 轴
        double[] modelDepths = new double[count];
        Arrays.fill(modelDepths, 5.0); // 可以根据模型类型定深度
        double[] optimizedZ = {0.0};
        try {
            optimizedZ = LayoutGA.optimizeZPositionsGA(count, roadLength, 2.0, modelDepths, 500, 100);
        } catch (IOException e) {
            // 记录异常并回退到默认 Z 轴位置
            System.err.println("GA Z 轴优化失败: " + e.getMessage());
        }

        baseY = switch(type) {
            case "drive-lane" -> 0.22 + elevation;
            case "bus-lane" -> 0.28 + elevation;
            case "parking-lane" -> 0.18 + elevation;
            case "flex-zone" -> 0.2 + elevation;
            case "temporary" -> 0.12 + elevation;
            case "sidewalk-tree" -> 0.28 + elevation;
            case "sidewalk" -> 0.18 + elevation;
            default -> elevation;
        };

        double rotationY = resolveSegmentRotationY(segment);

        for (int i = 0; i < count; i++) {
            SceneInstanceDTO instance = createSegmentInstance(
                    segment,
                    "model",
                    type + "-instance",
                    centerX + offsetX,
                    baseY,
                    optimizedZ[i],
                    rotationY
            );

            // 设置尺寸和颜色
            setInstanceSizeAndColor(type, instance);
            instances.add(instance);
        }
    }

    private void setInstanceSizeAndColor(String type, SceneInstanceDTO instance) {
        switch(type) {
            case "drive-lane": instance.setWidth(1.9); instance.setHeight(1.6); instance.setDepth(4.8); instance.setColor("#9ca3af"); break;
            case "bus-lane": instance.setWidth(2.6); instance.setHeight(2.9); instance.setDepth(10.5); instance.setColor("#ef4444"); break;
            case "parking-lane": instance.setWidth(1.9); instance.setHeight(1.6); instance.setDepth(4.8); instance.setColor("#6b7280"); break;
            case "flex-zone": instance.setWidth(1.9); instance.setHeight(1.6); instance.setDepth(4.8); instance.setColor("#f59e0b"); break;
            case "temporary": instance.setWidth(0.35); instance.setHeight(0.8); instance.setDepth(0.35); instance.setColor("#f97316"); break;
            case "sidewalk-tree": instance.setWidth(1.2); instance.setHeight(4.5); instance.setDepth(1.2); instance.setColor("#65a30d"); break;
            case "sidewalk": instance.setWidth(0.45); instance.setHeight(1.7); instance.setDepth(0.45); instance.setColor("#60a5fa"); break;
        }
    }

    private BoundarySceneDTO buildBoundaryScene(
            BoundaryPreviewDTO boundary,
            String side,
            double totalRoadWidth,
            double roadLength
    ) {
        if (boundary == null) {
            return null;
        }

        BoundaryPlacement placement = resolveBoundaryPlacement(boundary.getType());
        double sideSign = "left".equalsIgnoreCase(side) ? -1.0 : 1.0;
        double streetHalfWidth = totalRoadWidth / 2.0;
        double centerX = sideSign * (streetHalfWidth + placement.footprintWidth / 2.0 + placement.roadGap);

        double supportHeight = Math.max(
                placement.boxHeight,
                safeElevation(boundary.getElevation()) > 0.0
                        ? safeElevation(boundary.getElevation()) + 0.12
                        : placement.boxHeight
        );

        double baseY = -DEFAULT_BASE_HEIGHT / 2.0 - 0.02;
        double baseTopY = baseY + DEFAULT_BASE_HEIGHT / 2.0;

        BoundarySceneDTO dto = new BoundarySceneDTO();
        dto.setBoundaryId(boundary.getBoundaryId());
        dto.setSide(side);
        dto.setType(boundary.getType());
        dto.setFloors(boundary.getFloors());
        dto.setElevation(safeElevation(boundary.getElevation()));
        dto.setVariantData(boundary.getVariantData());
        dto.setCenterX(centerX);
        dto.setSupportHeight(supportHeight);

        SceneInstanceDTO support = new SceneInstanceDTO();
        support.setKind("support");
        support.setSemanticType("boundary-support");
        support.setPosition(new SceneVector3DTO(centerX, baseTopY + supportHeight / 2.0, 0));
        support.setRotation(new SceneVector3DTO(0, 0, 0));
        support.setScale(new SceneVector3DTO(1, 1, 1));
        support.setWidth(placement.footprintWidth);
        support.setHeight(supportHeight);
        support.setDepth(Math.max(placement.footprintDepth, roadLength));
        support.setColor("#c8b89a");
        dto.setSupportSurface(support);

        appendBoundaryInstances(dto, boundary, side, centerX, baseTopY, supportHeight, roadLength, placement);

        return dto;
    }

    private SceneBaseDTO buildSceneBase(
            double totalRoadWidth,
            double roadLength,
            BoundarySceneDTO leftBoundary,
            BoundarySceneDTO rightBoundary
    ) {
        double leftWidth = leftBoundary != null && leftBoundary.getSupportSurface() != null
                ? safeNullable(leftBoundary.getSupportSurface().getWidth())
                : 0.0;
        double rightWidth = rightBoundary != null && rightBoundary.getSupportSurface() != null
                ? safeNullable(rightBoundary.getSupportSurface().getWidth())
                : 0.0;

        double leftDepth = leftBoundary != null && leftBoundary.getSupportSurface() != null
                ? safeNullable(leftBoundary.getSupportSurface().getDepth())
                : 50.0;
        double rightDepth = rightBoundary != null && rightBoundary.getSupportSurface() != null
                ? safeNullable(rightBoundary.getSupportSurface().getDepth())
                : 50.0;

        SceneBaseDTO base = new SceneBaseDTO();
        base.setWidth(totalRoadWidth + leftWidth + rightWidth);
        base.setHeight(DEFAULT_BASE_HEIGHT);
        base.setDepth(Math.max(roadLength, Math.max(leftDepth, rightDepth)));
        base.setPosition(new SceneVector3DTO(0, -DEFAULT_BASE_HEIGHT / 2.0 - 0.02, 0));
        base.setColor("#c8b89a");
        return base;
    }

    private double resolveSegmentRotationY(SegmentPreviewDTO segment) {
        String flowDirection = readText(segment.getVariantData(), "flowDirection", "direction");
        if ("inbound".equalsIgnoreCase(flowDirection)) {
            return 0.0;
        }
        return 180.0;
    }

    private String resolveSegmentMaterialKey(String type, JsonNode variantData) {
        String laneStyle = readText(variantData, "laneStyle", "color", "variant");

        if ("bus-lane".equals(type)) {
            if ("colored".equalsIgnoreCase(laneStyle) || "red".equalsIgnoreCase(laneStyle)) {
                return "surface-red bus-lane";
            }
            return "bus-lane";
        }

        if ("drive-lane".equals(type)) {
            return "drive-lane";
        }

        if ("parking-lane".equals(type)) {
            return "parking-lane";
        }

        if ("sidewalk".equals(type) || "sidewalk-tree".equals(type)) {
            return "sidewalk";
        }

        if ("flex-zone".equals(type)) {
            return "flex-zone";
        }

        if ("temporary".equals(type)) {
            return "temporary";
        }

        return type;
    }

    private String resolveSegmentColor(String type, JsonNode variantData) {
        String laneStyle = readText(variantData, "laneStyle", "color", "variant");

        switch (type) {
            case "sidewalk":
                return "#d1d5db";
            case "sidewalk-tree":
                return "#84cc16";
            case "drive-lane":
                return "#4b5563";
            case "bus-lane":
                return ("colored".equalsIgnoreCase(laneStyle) || "red".equalsIgnoreCase(laneStyle))
                        ? "#b91c1c"
                        : "#4b5563";
            case "parking-lane":
                return "#6b7280";
            case "temporary":
                return "#ffffff";
            case "flex-zone":
                return "#9ca3af";
            default:
                return "#9ca3af";
        }
    }

    private String resolveSkyColor(String skybox) {
        if (skybox == null) {
            return "#cfdced";
        }
        switch (skybox) {
            case "night":
                return "#2b3440";
            case "sunset":
                return "#d7c3a3";
            case "overcast":
                return "#c7d0d8";
            case "day":
            default:
                return "#cfdced";
        }
    }

    private SceneVector3DTO parseVector3(String raw, SceneVector3DTO defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length != 3) {
            return defaultValue;
        }
        try {
            return new SceneVector3DTO(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2])
            );
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String readText(JsonNode jsonNode, String... keys) {
        if (jsonNode == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (jsonNode.has(key) && !jsonNode.get(key).isNull()) {
                return jsonNode.get(key).asText();
            }
        }
        return null;
    }

    private String safeText(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private double safeWidth(Double value) {
        return value == null ? 0.0 : Math.max(0.0, value);
    }

    private double safeElevation(Double value) {
        return value == null ? 0.0 : Math.max(0.0, value);
    }

    private double safeNullable(Double value) {
        return value == null ? 0.0 : value;
    }

    private BoundaryPlacement resolveBoundaryPlacement(String type) {
        if (type == null || type.isBlank()) {
            return new BoundaryPlacement(
                    "1 1 1",   // scale
                    "0 0 0",   // rotation
                    6,         // footprintWidth
                    30,        // footprintDepth
                    2,         // roadGap
                    0.0,       // modelYOffset
                    0.0,       // modelOffsetX
                    0.0,       // modelOffsetZ
                    1.2        // boxHeight
            );
        }

        String lower = type.trim().toLowerCase();

        switch (lower) {
            /**
             * 建筑类：默认靠近道路，纵深较大，支撑高度略高一点
             * 适合 residential / narrow / wide / arcade
             */
            case "residential":
                return new BoundaryPlacement(
                        "1 1 1",
                        "0 0 0",
                        8,
                        30,
                        2,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                );

            case "narrow":
                return new BoundaryPlacement(
                        "0.9 1 1",
                        "0 0 0",
                        5,
                        28,
                        4,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                );

            case "wide":
                return new BoundaryPlacement(
                        "0.8 1 1",
                        "0 0 0",
                        12,
                        32,
                        4,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                );

            case "arcade":
                return new BoundaryPlacement(
                        "1 1 1",
                        "0 0 0",
                        10,
                        30,
                        1.5,    // 骑楼/贴街感更强
                        0.0,
                        0.0,
                        0.0,
                        0.0
                );

            /**
             * 围挡/墙类：应当贴边、低矮、不要离道路太远
             */
            case "fence":
                return new BoundaryPlacement(
                        "1 1 1",
                        "0 0 0",
                        1.2,
                        30,
                        0.4,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                );

            case "compound-wall":
                return new BoundaryPlacement(
                        "1 1 1",
                        "0 0 0",
                        2.0,
                        30,
                        0.6,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                );

            /**
             * 绿地类：更像低矮场景面，不应抬得太高，也不该离路太远
             */
            case "grass":
                return new BoundaryPlacement(
                        "3.2 3 4",
                        "0 0 0",
                        6,
                        30,
                        28.5,
                        0.152,
                        0.0,
                        0.0,
                        0.152
                );

            /**
             * 特殊场景类
             */
            case "parking-lot":
                return new BoundaryPlacement(
                        "1 1 1",
                        "0 0 0",
                        14,
                        25,
                        23,
                        0.152,
                        0.0,
                        0.0,
                        0.152
                );

            case "waterfront":
                return new BoundaryPlacement(
                        "0.4 0.5 0.5",
                        "0 0 0",
                        14,
                        5,
                        33,
                        0.152,
                        0.0,
                        0.0,
                        0.152
                );

            /**
             * 未知类型兜底：
             * 不要太极端，给一个中性建筑/边界参数
             */
            default:
                return new BoundaryPlacement(
                        "1 1 1",
                        "0 0 0",
                        6,
                        30,
                        2,
                        0.0,
                        0.0,
                        0.0,
                        1.0
                );
        }
    }

    private static class BoundaryPlacement {
        private final String scale;
        private final String rotation;
        private final double footprintWidth;
        private final double footprintDepth;
        private final double roadGap;
        private final double modelYOffset;
        private final double modelOffsetX;
        private final double modelOffsetZ;
        private final double boxHeight;

        private BoundaryPlacement(
                String scale,
                String rotation,
                double footprintWidth,
                double footprintDepth,
                double roadGap,
                double modelYOffset,
                double modelOffsetX,
                double modelOffsetZ,
                double boxHeight
        ) {
            this.scale = scale;
            this.rotation = rotation;
            this.footprintWidth = footprintWidth;
            this.footprintDepth = footprintDepth;
            this.roadGap = roadGap;
            this.modelYOffset = modelYOffset;
            this.modelOffsetX = modelOffsetX;
            this.modelOffsetZ = modelOffsetZ;
            this.boxHeight = boxHeight;
        }
    }

    private void appendBoundaryInstances(
            BoundarySceneDTO dto,
            BoundaryPreviewDTO boundary,
            String side,
            double centerX,
            double baseTopY,
            double supportHeight,
            double roadLength,
            BoundaryPlacement placement
    ) {
        if (boundary == null || boundary.getType() == null || boundary.getType().isBlank()) {
            return;
        }

        String type = boundary.getType().trim().toLowerCase();
        double sideSign = "left".equalsIgnoreCase(side) ? -1.0 : 1.0;

        // 保留特殊场景类单模型逻辑
        if ("parking-lot".equals(type) || "waterfront".equals(type) || "grass".equals(type)) {
            if (boundary.getModel() == null
                    || boundary.getModel().getModelUrl() == null
                    || boundary.getModel().getModelUrl().isBlank()) {
                return;
            }

            SceneInstanceDTO model = new SceneInstanceDTO();
            model.setKind("model");
            model.setSemanticType("boundary-" + type);
            model.setModelId(boundary.getModel().getModelId());
            model.setModelUrl(boundary.getModel().getModelUrl());
            model.setDisplayName(boundary.getModel().getDisplayName());
            model.setPosition(new SceneVector3DTO(
                    centerX + sideSign * placement.modelOffsetX,
                    baseTopY + supportHeight + placement.modelYOffset,
                    placement.modelOffsetZ
            ));
            model.setRotation(parseVector3(placement.rotation, new SceneVector3DTO(0, 0, 0)));
            model.setScale(parseVector3(placement.scale, new SceneVector3DTO(1, 1, 1)));
            dto.getInstances().add(model);
            return;
        }

        List<ModelAsset> candidates = findBoundaryCandidateModels(type);
        if (candidates.isEmpty()) {
            return;
        }

        int targetCount = resolveBoundaryTargetCount(type, placement, roadLength);
        if (targetCount <= 0) {
            return;
        }

        List<ModelAsset> pickedModels = pickRandomModels(candidates, targetCount);

        // ========== 新增：提取每个模型的实际深度（考虑 scale） ==========
        SceneVector3DTO scaleVec = parseVector3(placement.scale, new SceneVector3DTO(1, 1, 1));
        double scaleZ = scaleVec.getZ();  // Z 轴缩放因子
        double[] modelDepths = new double[targetCount];
        for (int i = 0; i < targetCount; i++) {
            ModelAsset model = pickedModels.get(i);
            double originalDepth = 10.0; // 默认深度 4 米
            modelDepths[i] = originalDepth * scaleZ;
        }
        // ================================================================

        double[] zPositions = buildBoundaryZPositions(type, targetCount, roadLength, placement, modelDepths);
        double instanceY = baseTopY + supportHeight + placement.modelYOffset;
        SceneVector3DTO rotation = parseVector3(resolveBoundaryRotation(type, side), new SceneVector3DTO(0, 0, 0));
        SceneVector3DTO scale = parseVector3(placement.scale, new SceneVector3DTO(1, 1, 1));

        for (int i = 0; i < targetCount; i++) {
            ModelAsset modelAsset = pickedModels.get(i);

            SceneInstanceDTO instance = new SceneInstanceDTO();
            instance.setKind("model");
            instance.setSemanticType("boundary-" + type);
            instance.setModelId(modelAsset.getModelId());
            instance.setModelUrl(modelAsset.getModelUrl());
            instance.setDisplayName(modelAsset.getDisplayName());
            instance.setPosition(new SceneVector3DTO(
                    centerX + sideSign * placement.modelOffsetX,
                    instanceY,
                    zPositions[i] + placement.modelOffsetZ
            ));
            instance.setRotation(rotation);
            instance.setScale(scale);

            dto.getInstances().add(instance);
        }
    }
    private List<ModelAsset> findBoundaryCandidateModels(String boundaryType) {
        String normalized = boundaryType == null ? "" : boundaryType.trim().toLowerCase();

        String modelType;
        switch (normalized) {
            case "residential":
            case "narrow":
            case "wide":
            case "arcade":
                modelType = "Building";
                break;
            case "fence":
            case "grass":
                modelType = "Scene";
                break;
            case "compound-wall":
                modelType = "Props";
                break;
            default:
                return Collections.emptyList();
        }

        LambdaQueryWrapper<ModelAsset> wrapper = new LambdaQueryWrapper<ModelAsset>()
                .eq(ModelAsset::getModelType, modelType)
                .eq(ModelAsset::getModelSubtype, normalized)
                .orderByAsc(ModelAsset::getDisplayName);

        return modelAssetMapper.selectList(wrapper);
    }
    private int resolveBoundaryTargetCount(String type, BoundaryPlacement placement, double roadLength) {
        String normalized = type == null ? "" : type.trim().toLowerCase();

        switch (normalized) {
            case "residential":
                return 3;
            case "narrow":
                return 6;
            case "wide":
                return 4;
            case "arcade":
                return 3;
            case "fence":
                return 5;
            case "compound-wall":
                return 4;
            default:
                return Math.max(1, (int) Math.round(roadLength / 24.0));
        }
    }
    private List<ModelAsset> pickRandomModels(List<ModelAsset> candidates, int targetCount) {
        List<ModelAsset> results = new ArrayList<>();
        if (candidates == null || candidates.isEmpty() || targetCount <= 0) {
            return results;
        }

        List<ModelAsset> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);

        if (shuffled.size() >= targetCount) {
            return new ArrayList<>(shuffled.subList(0, targetCount));
        }

        int index = 0;
        while (results.size() < targetCount) {
            results.add(shuffled.get(index % shuffled.size()));
            index++;
        }

        return results;
    }
    /**
     * 使用遗传算法优化 boundary 内模型的 Z 轴位置，考虑每个模型的深度
     */
    private double[] buildBoundaryZPositions(
            String type,
            int count,
            double roadLength,
            BoundaryPlacement placement,
            double[] modelDepths   // 新增参数
    ) {
        if (count <= 1) return new double[]{0.0};

        // 边界缓冲，可根据类型调整
        double edgePadding = 2.0;
        double[] optimizedZ = {0.0};
        try {
            optimizedZ = LayoutGA.optimizeZPositionsGA(count, roadLength, 2.0, modelDepths, 500, 100);
        } catch (IOException e) {
            // 记录异常并回退到默认 Z 轴位置
            System.err.println("GA Z 轴优化失败: " + e.getMessage());
        }
        // 调用 GA 优化，传入模型深度数组
        return optimizedZ;
    }
    private String resolveBoundaryRotation(String type, String side) {
        String normalizedType = type == null ? "" : type.trim().toLowerCase();
        boolean isLeft = "left".equalsIgnoreCase(side);

        switch (normalizedType) {
            case "residential":
            case "narrow":
            case "wide":
                return isLeft ? "0 -90 0" : "0 90 0";
            case "arcade":
                return isLeft ? "0 90 0" : "0 -90 0";

            case "fence":
            case "compound-wall":
                return isLeft ? "0 90 0" : "0 -90 0";

            default:
                return "0 0 0";
        }
    }
}