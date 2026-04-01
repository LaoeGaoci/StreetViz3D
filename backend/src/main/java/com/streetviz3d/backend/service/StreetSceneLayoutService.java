package com.streetviz3d.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.streetviz3d.backend.dto.response.StreetPreviewResponse;
import com.streetviz3d.backend.dto.scene.*;
import com.streetviz3d.backend.dto.street.BoundaryPreviewDTO;
import com.streetviz3d.backend.dto.street.SegmentPreviewDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StreetSceneLayoutService {

    private static final double DEFAULT_ROAD_LENGTH = 80.0;
    private static final double DEFAULT_WIDTH_SCALE = 1.5;
    private static final double DEFAULT_BASE_HEIGHT = 3.0;

    private final StreetPreviewService streetPreviewService;

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

        if ("drive-lane".equals(type)) {
            String vehicleType = readText(variantData, "vehicleType");
            String flowDirection = readText(variantData, "flowDirection");
            double rotationY = "inbound".equalsIgnoreCase(flowDirection) ? 0.0 : 180.0;

            double[] zPositions = new double[]{-16.0, 14.0};
            for (double z : zPositions) {
                SceneInstanceDTO vehicle = createSegmentInstance(
                        segment,
                        "model",
                        "drive-lane-" + safeText(vehicleType, "vehicle"),
                        centerX,
                        0.22 + elevation,
                        z,
                        rotationY
                );
                vehicle.setColor("#9ca3af");
                vehicle.setWidth(1.9);
                vehicle.setHeight(1.6);
                vehicle.setDepth(4.8);
                instances.add(vehicle);
            }
            return;
        }

        if ("bus-lane".equals(type)) {
            String flowDirection = readText(variantData, "flowDirection");
            double rotationY = "inbound".equalsIgnoreCase(flowDirection) ? 0.0 : 180.0;

            SceneInstanceDTO bus = createSegmentInstance(
                    segment,
                    "model",
                    "bus-lane-bus",
                    centerX,
                    0.28 + elevation,
                    0,
                    rotationY
            );
            bus.setColor("#ef4444");
            bus.setWidth(2.6);
            bus.setHeight(2.9);
            bus.setDepth(10.5);
            instances.add(bus);
            return;
        }

        if ("parking-lane".equals(type)) {
            String parkingDirection = readText(variantData, "parkingDirection");
            String placementSide = readText(variantData, "placementSide");

            double rotationY = "inbound".equalsIgnoreCase(parkingDirection) ? 180.0 : 0;
            double offsetX = "left".equalsIgnoreCase(placementSide) ? -0.2 : 0.2;

            double[] zPositions = new double[]{-22.0, -8.0, 8.0, 22.0};
            for (double z : zPositions) {
                SceneInstanceDTO parkedCar = createSegmentInstance(
                        segment,
                        "model",
                        "parking-lane-parked-car",
                        centerX + offsetX,
                        0.18 + elevation,
                        z,
                        rotationY
                );
                parkedCar.setColor("#6b7280");
                parkedCar.setWidth(1.9);
                parkedCar.setHeight(1.6);
                parkedCar.setDepth(4.8);
                instances.add(parkedCar);
            }
            return;
        }

        if ("flex-zone".equals(type)) {
            String flowDirection = readText(variantData, "flowDirection");
            String placementSide = readText(variantData, "placementSide");
            double rotationY = "inbound".equalsIgnoreCase(flowDirection) ? 0.0 : 180.0;

            double[] zPositions = new double[]{-12.0, 10.0};
            double offsetX = "left".equalsIgnoreCase(placementSide) ? -0.15 : 0.15;

            for (double z : zPositions) {
                SceneInstanceDTO taxi = createSegmentInstance(
                        segment,
                        "model",
                        "flex-zone-taxi",
                        centerX + offsetX,
                        0.2 + elevation,
                        z,
                        rotationY
                );
                taxi.setColor("#f59e0b");
                taxi.setWidth(1.9);
                taxi.setHeight(1.6);
                taxi.setDepth(4.8);
                instances.add(taxi);
            }
            return;
        }

        if ("temporary".equals(type)) {
            String barrierType = readText(variantData, "barrierType");

            for (double z = -roadLength / 2.0 + 10.0; z <= roadLength / 2.0 - 10.0; z += 10.0) {
                SceneInstanceDTO cone = createSegmentInstance(
                        segment,
                        "model",
                        "temporary-" + safeText(barrierType, "temporary-object"),
                        centerX,
                        0.12 + elevation,
                        z,
                        0
                );
                cone.setColor("#f97316");
                cone.setWidth(0.35);
                cone.setHeight(0.8);
                cone.setDepth(0.35);
                instances.add(cone);
            }
            return;
        }

        if ("sidewalk-tree".equals(type)) {
            String treeType = readText(variantData, "treeType");

            for (double z = -roadLength / 2.0 + 12.0; z <= roadLength / 2.0 - 12.0; z += 14.0) {
                SceneInstanceDTO tree = createSegmentInstance(
                        segment,
                        "model",
                        "sidewalk-tree-" + safeText(treeType, "tree"),
                        centerX,
                        0.28 + elevation,
                        z,
                        0
                );
                tree.setColor("#65a30d");
                tree.setWidth(1.2);
                tree.setHeight(4.5);
                tree.setDepth(1.2);
                instances.add(tree);
            }
            return;
        }

        if ("sidewalk".equals(type)) {
            String pedestrianDensity = readText(variantData, "pedestrianDensity");

            if ("empty".equalsIgnoreCase(pedestrianDensity)) {
                return;
            }

            double[] zPositions;
            if ("dense".equalsIgnoreCase(pedestrianDensity)) {
                zPositions = new double[]{-20, -12, -4, 4, 12, 20};
            } else if ("normal".equalsIgnoreCase(pedestrianDensity)) {
                zPositions = new double[]{-14, -2, 10};
            } else if ("sparse".equalsIgnoreCase(pedestrianDensity)) {
                zPositions = new double[]{0};
            } else {
                zPositions = new double[]{};
            }

            for (double z : zPositions) {
                SceneInstanceDTO pedestrian = createSegmentInstance(
                        segment,
                        "model",
                        "sidewalk-pedestrian",
                        centerX,
                        0.18 + elevation,
                        z,
                        0
                );
                pedestrian.setColor("#60a5fa");
                pedestrian.setWidth(0.45);
                pedestrian.setHeight(1.7);
                pedestrian.setDepth(0.45);
                instances.add(pedestrian);
            }
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

        if (boundary.getModel() != null
                && boundary.getModel().getModelUrl() != null
                && !boundary.getModel().getModelUrl().isBlank()) {

            SceneInstanceDTO model = new SceneInstanceDTO();
            model.setKind("model");
            model.setSemanticType("boundary-" + boundary.getType());
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
        }

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
        if ("parking-lot".equals(type)) {
            return new BoundaryPlacement("1 1 1", "0 0 0", 14, 25, 23, 0.152, 0, 0, 0.152);
        }
        if ("waterfront".equals(type)) {
            return new BoundaryPlacement("0.4 0.5 0.5", "0 0 0", 14, 5, 33, 0.152, 0, 0, 0.152);
        }
        return new BoundaryPlacement("1 1 1", "0 0 0", 3, 30, 0, 0, 0, 0, 1);
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
}