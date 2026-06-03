package com.streetviz3d.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streetviz3d.backend.dto.response.StreetPreviewResponse;
import com.streetviz3d.backend.dto.scene.*;
import com.streetviz3d.backend.entity.LayoutGA;
import com.streetviz3d.backend.entity.ModelAsset;
import com.streetviz3d.backend.mapper.ModelAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 街景智构服务类。
 *
 * 主要职责：
 * 1. 接收前端上传的真实街景图片；
 * 2. 调用 FastAPI 中的 Mask2Former 服务，获取街景语义类别序列；
 * 3. 将识别出的类别映射为 StreetViz3D 前端可渲染的 Segment / Boundary；
 * 4. 参考 StreetSceneLayoutService 的布局规则，从左到右居中排布 segment；
 * 5. 根据每个类别预设实体数量，在对应 segment 上生成模型实例；
 * 6. 调用 LayoutGA 对每个 segment / boundary 内模型实例的 Z 轴位置进行优化；
 * 7. 返回完整 StreetSceneDTO，供前端 A-Frame / Three.js 渲染。
 */
@Service
@RequiredArgsConstructor
public class StreetPredictService {

    /**
     * 预测生成街道的默认纵向长度。
     * Z 轴方向使用该长度作为模型实例排布范围。
     */
    private static final double DEFAULT_ROAD_LENGTH = 80.0;

    /**
     * X 轴方向的宽度缩放比例。
     * 与 StreetSceneLayoutService 中 DEFAULT_WIDTH_SCALE 保持一致。
     */
    private static final double DEFAULT_WIDTH_SCALE = 1.5;

    /**
     * 场景底座高度。
     * 用于计算 base 和 boundary support surface 的位置。
     */
    private static final double DEFAULT_BASE_HEIGHT = 3.0;

    /**
     * 默认 segment 原始宽度。
     * 不同类别可以在 mapCategoryToSegmentSpec 中单独覆盖。
     */
    private static final double DEFAULT_SEGMENT_WIDTH = 3.0;

    /**
     * GA 优化时，模型与道路边缘之间保留的安全距离。
     */
    private static final double EDGE_PADDING = 2.0;

    /**
     * GA 迭代次数。
     */
    private static final int GA_GENERATIONS = 500;

    /**
     * GA 种群规模。
     */
    private static final int GA_POPULATION_SIZE = 100;

    private final ObjectMapper objectMapper;
    private final ModelAssetMapper modelAssetMapper;

    /**
     * 用于调用 FastAPI 模型服务。
     *
     * 当前服务地址写死为 http://localhost:8000/predict。
     * 如果后续部署到服务器，建议改为 application.yml 配置项。
     */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 前端上传街景图片后的主入口。
     *
     * 执行流程：
     * 1. 调用 FastAPI 获取类别字符串列表；
     * 2. 查询数据库中所有模型资源；
     * 3. 将类别拆分为 boundary 类别和 segment 类别；
     * 4. 构建道路内部 segments；
     * 5. 构建左右 boundary；
     * 6. 构建 base / style；
     * 7. 封装 StreetPreviewResponse 返回前端。
     */
    public StreetPreviewResponse processStreetImageForFrontend(MultipartFile file) throws IOException {
        List<String> detectedClasses = callMask2FormerAPI(file);

        List<ModelAsset> allModels = modelAssetMapper.selectList(null);

        List<String> boundaryTypes = new ArrayList<>();
        List<PredictSegmentSpec> segmentSpecs = new ArrayList<>();

        for (String rawClass : detectedClasses) {
            String category = normalize(rawClass);

            if (category == null) {
                continue;
            }

            if (isBoundaryCategory(category)) {
                boundaryTypes.add(category);
                continue;
            }

            PredictSegmentSpec spec = mapCategoryToSegmentSpec(category);

            if (spec != null) {
                segmentSpecs.add(spec);
            }
        }

        /*
         * 如果模型没有识别出任何可用道路 segment，则给一个默认 drive-lane，
         * 避免前端收到空场景。
         */
        if (segmentSpecs.isEmpty()) {
            segmentSpecs.add(new PredictSegmentSpec("drive-lane", "road", DEFAULT_SEGMENT_WIDTH));
        }

        StreetSceneDTO scene = new StreetSceneDTO();
        scene.setStreetId(UUID.randomUUID().toString());
        scene.setStreetName("PredictedStreet");
        scene.setRoadLength(DEFAULT_ROAD_LENGTH);
        scene.setWidthScale(DEFAULT_WIDTH_SCALE);
        scene.setStyle(buildSceneStyle());

        /*
         * 构建道路内部 segments。
         * 这里参考 StreetSceneLayoutService：
         * 先计算 totalRenderedWidth，再从 -totalRenderedWidth / 2.0 开始从左向右排布。
         */
        List<SegmentSceneDTO> segments = buildPredictedSegments(segmentSpecs, allModels);
        scene.setSegments(segments);

        double totalRenderedRoadWidth = segments.stream()
                .mapToDouble(SegmentSceneDTO::getRenderedWidth)
                .sum();

        BoundarySceneDTO leftBoundary = buildPredictedBoundary(
                boundaryTypes,
                "left",
                totalRenderedRoadWidth,
                DEFAULT_ROAD_LENGTH,
                allModels
        );

        BoundarySceneDTO rightBoundary = buildPredictedBoundary(
                boundaryTypes,
                "right",
                totalRenderedRoadWidth,
                DEFAULT_ROAD_LENGTH,
                allModels
        );

        scene.setLeftBoundary(leftBoundary);
        scene.setRightBoundary(rightBoundary);
        scene.setBase(buildSceneBase(totalRenderedRoadWidth, DEFAULT_ROAD_LENGTH, leftBoundary, rightBoundary));

        /*
         * streetWidth 这里保存道路内部 segment 的总渲染宽度，
         * 不包含 boundary 的支撑宽度。
         */
        scene.setStreetWidth(totalRenderedRoadWidth);

        StreetPreviewResponse response = new StreetPreviewResponse();
        response.setScene(scene);
        return response;
    }

    /**
     * 构建预测得到的 segment 列表。
     *
     * 布局规则：
     * 1. 每个 segment 有 originalWidth；
     * 2. renderedWidth = originalWidth * DEFAULT_WIDTH_SCALE；
     * 3. 先计算所有 segment 的 totalRenderedWidth；
     * 4. cursor 从 -totalRenderedWidth / 2.0 开始；
     * 5. 每个 segment 依次计算 startX / centerX / endX；
     * 6. 每个 segment 再生成 surface 和 instances。
     */
    private List<SegmentSceneDTO> buildPredictedSegments(
            List<PredictSegmentSpec> specs,
            List<ModelAsset> allModels
    ) {
        List<SegmentSceneDTO> results = new ArrayList<>();

        double totalRenderedWidth = specs.stream()
                .mapToDouble(spec -> spec.width * DEFAULT_WIDTH_SCALE)
                .sum();

        double cursor = -totalRenderedWidth / 2.0;

        for (int i = 0; i < specs.size(); i++) {
            PredictSegmentSpec spec = specs.get(i);

            double renderedWidth = spec.width * DEFAULT_WIDTH_SCALE;
            double startX = cursor;
            double centerX = startX + renderedWidth / 2.0;
            double endX = startX + renderedWidth;

            cursor = endX;

            SegmentSceneDTO segment = new SegmentSceneDTO();
            segment.setSegmentId("predict-seg-" + i);
            segment.setSortIndex(i);
            segment.setType(spec.segmentType);
            segment.setOriginalWidth(spec.width);
            segment.setRenderedWidth(renderedWidth);
            segment.setStartX(startX);
            segment.setCenterX(centerX);
            segment.setEndX(endX);
            segment.setElevation(0.0);
            segment.setElevationY(0.0);

            segment.setSurface(buildSegmentSurface(
                    spec.segmentType,
                    centerX,
                    renderedWidth,
                    DEFAULT_ROAD_LENGTH
            ));

            segment.setInstances(buildSegmentInstances(
                    spec,
                    centerX,
                    DEFAULT_ROAD_LENGTH,
                    allModels
            ));

            results.add(segment);
        }

        return results;
    }

    /**
     * 构建 segment 的地面表面。
     *
     * surface 只是道路片段表面，不是模型实例。
     * 例如：
     * - drive-lane 对应普通车道；
     * - bus-lane 对应公交车道；
     * - bike-lane 对应自行车道；
     * - sidewalk-modern 对应路灯、交通灯、交通标志所在的人行道设施区域；
     * - sidewalk-tree 对应绿化区域。
     */
    private SegmentSurfaceDTO buildSegmentSurface(
            String segmentType,
            double centerX,
            double renderedWidth,
            double roadLength
    ) {
        SegmentSurfaceDTO surface = new SegmentSurfaceDTO();
        surface.setPosition(new SceneVector3DTO(centerX, 0.05, 0));
        surface.setWidth(renderedWidth);
        surface.setHeight(0.1);
        surface.setDepth(roadLength);
        surface.setColor(resolveSegmentColor(segmentType));
        surface.setMaterialKey(resolveSegmentMaterialKey(segmentType));
        return surface;
    }

    /**
     * 根据 segment 类型和原始识别类别生成实例。
     *
     * sourceCategory 是 FastAPI 返回的原始类别。
     * segmentType 是系统内部映射后的道路片段类型。
     *
     * 例如：
     * - sourceCategory = bicycle，segmentType = bike-lane；
     * - sourceCategory = motorcycle，segmentType = drive-lane；
     * - sourceCategory = pole，segmentType = sidewalk-modern。
     */
    private List<SceneInstanceDTO> buildSegmentInstances(
            PredictSegmentSpec spec,
            double centerX,
            double roadLength,
            List<ModelAsset> allModels
    ) {
        List<SceneInstanceDTO> instances = new ArrayList<>();

        InstanceRule rule = resolveInstanceRule(
                spec.segmentType,
                spec.sourceCategory,
                roadLength
        );

        if (rule.count <= 0) {
            return instances;
        }

        List<ModelAsset> candidates = findCandidateModels(spec.sourceCategory, allModels);

        if (candidates.isEmpty()) {
            System.err.println("未找到匹配模型，sourceCategory = "
                    + spec.sourceCategory
                    + ", segmentType = "
                    + spec.segmentType);
            return instances;
        }

        List<ModelAsset> pickedModels = pickRandomModels(candidates, rule.count);

        double[] modelDepths = new double[rule.count];
        Arrays.fill(modelDepths, rule.depth);

        double[] zPositions = optimizeZPositions(
                rule.count,
                roadLength,
                EDGE_PADDING,
                modelDepths,
                rule.defaultZ
        );

        for (int i = 0; i < rule.count; i++) {
            ModelAsset model = pickedModels.get(i);

            SceneInstanceDTO instance = new SceneInstanceDTO();
            instance.setKind("model");

            /*
             * semanticType 使用原始类别，方便前端展示真实识别结果。
             * 例如 bike-lane 上的模型仍然标记为 bicycle。
             */
            instance.setSemanticType(spec.sourceCategory);

            instance.setModelId(model.getModelId());
            instance.setModelUrl(model.getModelUrl());
            instance.setDisplayName(model.getDisplayName());

            instance.setPosition(new SceneVector3DTO(
                    centerX + rule.offsetX,
                    rule.y,
                    zPositions[i]
            ));

            instance.setRotation(new SceneVector3DTO(0, rule.rotationY, 0));
            instance.setScale(new SceneVector3DTO(1, 1, 1));

            instance.setWidth(rule.width);
            instance.setHeight(rule.height);
            instance.setDepth(rule.depth);
            instance.setColor(rule.color);

            instances.add(instance);
        }

        return instances;
    }

    /**
     * 构建预测场景的左右边界。
     *
     * 处理规则：
     * 1. FastAPI 识别到的 building / wall / fence 会进入 boundaryTypes；
     * 2. left 使用第一个 boundary 类型；
     * 3. right 使用最后一个 boundary 类型；
     * 4. 如果没有识别到边界，则默认使用 building；
     * 5. boundary 的 X 坐标根据道路总宽度、边界自身 footprintWidth 和 roadGap 计算。
     */
    private BoundarySceneDTO buildPredictedBoundary(
            List<String> boundaryTypes,
            String side,
            double totalRoadWidth,
            double roadLength,
            List<ModelAsset> allModels
    ) {
        BoundarySceneDTO boundary = new BoundarySceneDTO();
        boundary.setBoundaryId(side + "-boundary");
        boundary.setSide(side);
        boundary.setFloors(3);
        boundary.setElevation(0.0);
        boundary.setInstances(new ArrayList<>());

        String boundaryType = resolveBoundaryTypeForSide(boundaryTypes, side);
        boundary.setType(boundaryType);

        BoundaryPlacement placement = resolveBoundaryPlacement(boundaryType);

        double sideSign = "left".equalsIgnoreCase(side) ? -1.0 : 1.0;
        double streetHalfWidth = totalRoadWidth / 2.0;

        double centerX = sideSign * (
                streetHalfWidth
                        + placement.footprintWidth / 2.0
                        + placement.roadGap
        );

        boundary.setCenterX(centerX);
        boundary.setSupportHeight(placement.boxHeight);
        boundary.setSupportSurface(null);
        double baseY = -DEFAULT_BASE_HEIGHT / 2.0 - 0.02;
        double baseTopY = baseY + DEFAULT_BASE_HEIGHT / 2.0;

        appendBoundaryInstances(
                boundary,
                boundaryType,
                side,
                centerX,
                baseTopY,
                placement,
                roadLength,
                allModels
        );

        return boundary;
    }

    /**
     * 在 boundary 上生成建筑、围墙、围栏等模型实例。
     *
     * 当前数据库映射规则：
     * - building -> Building / narrow
     * - wall     -> Props / compound-wall
     * - fence    -> Scene / fence
     */
    private void appendBoundaryInstances(
            BoundarySceneDTO boundary,
            String boundaryType,
            String side,
            double centerX,
            double baseTopY,
            BoundaryPlacement placement,
            double roadLength,
            List<ModelAsset> allModels
    ) {
        if (boundaryType == null || boundaryType.isBlank()) {
            return;
        }

        List<ModelAsset> candidates = findBoundaryCandidateModels(boundaryType, allModels);

        if (candidates.isEmpty()) {
            System.err.println("未找到 boundary 匹配模型，boundaryType = " + boundaryType);
            return;
        }

        int count = resolveBoundaryTargetCount(boundaryType, roadLength);
        List<ModelAsset> pickedModels = pickRandomModels(candidates, count);

        double[] modelDepths = new double[count];
        Arrays.fill(modelDepths, placement.modelDepth);

        double[] zPositions = optimizeZPositions(
                count,
                roadLength,
                EDGE_PADDING,
                modelDepths,
                null
        );

        double sideSign = "left".equalsIgnoreCase(side) ? -1.0 : 1.0;
        double instanceY = baseTopY + placement.modelYOffset;

        SceneVector3DTO rotation = resolveBoundaryRotation(boundaryType, side);

        for (int i = 0; i < count; i++) {
            ModelAsset model = pickedModels.get(i);

            SceneInstanceDTO instance = new SceneInstanceDTO();
            instance.setKind("model");
            instance.setSemanticType("boundary-" + boundaryType);

            instance.setModelId(model.getModelId());
            instance.setModelUrl(model.getModelUrl());
            instance.setDisplayName(model.getDisplayName());

            instance.setPosition(new SceneVector3DTO(
                    centerX + sideSign * placement.modelOffsetX,
                    instanceY,
                    zPositions[i] + placement.modelOffsetZ
            ));

            instance.setRotation(rotation);
            instance.setScale(new SceneVector3DTO(1, 1, 1));

            instance.setWidth(placement.modelWidth);
            instance.setHeight(placement.modelHeight);
            instance.setDepth(placement.modelDepth);
            instance.setColor("#a3a3a3");

            boundary.getInstances().add(instance);
        }
    }

    /**
     * 构建场景底座。
     *
     * base 的宽度 = 道路总宽度 + 左边界支撑面宽度 + 右边界支撑面宽度。
     * base 的深度 = roadLength 和左右边界支撑面深度中的最大值。
     */
    private SceneBaseDTO buildSceneBase(
            double totalRoadWidth,
            double roadLength,
            BoundarySceneDTO leftBoundary,
            BoundarySceneDTO rightBoundary
    ) {
        double leftWidth = leftBoundary != null && leftBoundary.getSupportSurface() != null
                ? safe(leftBoundary.getSupportSurface().getWidth())
                : 0.0;

        double rightWidth = rightBoundary != null && rightBoundary.getSupportSurface() != null
                ? safe(rightBoundary.getSupportSurface().getWidth())
                : 0.0;

        double leftDepth = leftBoundary != null && leftBoundary.getSupportSurface() != null
                ? safe(leftBoundary.getSupportSurface().getDepth())
                : roadLength;

        double rightDepth = rightBoundary != null && rightBoundary.getSupportSurface() != null
                ? safe(rightBoundary.getSupportSurface().getDepth())
                : roadLength;

        SceneBaseDTO base = new SceneBaseDTO();
        base.setWidth(totalRoadWidth + leftWidth + rightWidth);
        base.setHeight(DEFAULT_BASE_HEIGHT);
        base.setDepth(Math.max(roadLength, Math.max(leftDepth, rightDepth)));
        base.setPosition(new SceneVector3DTO(0, -DEFAULT_BASE_HEIGHT / 2.0 - 0.02, 0));
        base.setColor("#c8b89a");

        return base;
    }

    /**
     * 构建默认场景光照与天空颜色。
     */
    private SceneStyleDTO buildSceneStyle() {
        SceneStyleDTO style = new SceneStyleDTO();
        style.setSkyColor("#cfdced");
        style.setAmbientLightColor("#ffffff");
        style.setAmbientLightIntensity(1.0);
        style.setDirectionalLightColor("#ffffff");
        style.setDirectionalLightIntensity(1.05);
        return style;
    }

    /**
     * 将 FastAPI 返回的语义类别映射为 StreetViz3D 内部 segment。
     *
     * 当前规则：
     * - bicycle -> bike-lane
     * - motorcycle -> drive-lane
     * - pole / traffic light / traffic sign -> sidewalk-modern
     * - vegetation -> sidewalk-tree
     * - building / wall / fence 不在这里处理，而是在 boundary 中处理
     */
    private PredictSegmentSpec mapCategoryToSegmentSpec(String category) {
        return switch (category) {
            case "road" ->
                    new PredictSegmentSpec("drive-lane", "road", 3.2);

            case "car", "truck", "train", "motorcycle" ->
                    new PredictSegmentSpec("drive-lane", category, 3.2);

            case "bus" ->
                    new PredictSegmentSpec("bus-lane", category, 3.5);

            case "bicycle" ->
                    new PredictSegmentSpec("bike-lane", category, 2.4);

            case "sidewalk", "person", "rider" ->
                    new PredictSegmentSpec("sidewalk", category, 3.0);

            case "vegetation" ->
                    new PredictSegmentSpec("sidewalk-tree", category, 3.0);

            case "pole", "traffic light", "traffic sign" ->
                    new PredictSegmentSpec("sidewalk-modern", category, 2.2);

            default -> null;
        };
    }

    /**
     * 定义每类 segment 上生成多少实例，以及模型尺寸、颜色、默认高度等。
     *
     * sourceCategory 是原始类别；
     * segmentType 是映射后的道路片段类型。
     */
    private InstanceRule resolveInstanceRule(
            String segmentType,
            String sourceCategory,
            double roadLength
    ) {
        return switch (segmentType) {
            case "drive-lane" -> {
                /*
                 * road 本身只生成路面，不额外生成模型。
                 */
                if ("road".equals(sourceCategory)) {
                    yield InstanceRule.empty();
                }

                int count = switch (sourceCategory) {
                    case "truck", "train" -> 1;
                    case "motorcycle" -> 2;
                    default -> 3;
                };

                double width = switch (sourceCategory) {
                    case "truck" -> 2.4;
                    case "motorcycle" -> 1.0;
                    default -> 1.9;
                };

                double height = switch (sourceCategory) {
                    case "truck" -> 2.8;
                    case "motorcycle" -> 1.5;
                    default -> 1.6;
                };

                double depth = switch (sourceCategory) {
                    case "truck" -> 8.0;
                    case "train" -> 12.0;
                    case "motorcycle" -> 2.5;
                    default -> 4.8;
                };

                yield new InstanceRule(
                        count,
                        width,
                        height,
                        depth,
                        0.22,
                        0.0,
                        0.0,
                        "#9ca3af",
                        new double[]{-18.0, 0.0, 18.0}
                );
            }

            case "bus-lane" -> new InstanceRule(
                    1,
                    2.6,
                    2.9,
                    10.5,
                    0.28,
                    0.0,
                    90.0,
                    "#ef4444",
                    new double[]{0.0}
            );

            case "bike-lane" -> new InstanceRule(
                    3,
                    1.8,
                    1.5,
                    3.0,
                    0.18,
                    0.0,
                    90.0,
                    "#22c55e",
                    new double[]{-20.0, 0.0, 20.0}
            );

            case "sidewalk-modern" -> {
                int count = switch (sourceCategory) {
                    case "traffic light" -> 2;
                    case "traffic sign" -> 3;
                    case "pole" -> 7;
                    default -> 3;
                };

                double height = switch (sourceCategory) {
                    case "traffic light" -> 4.0;
                    case "traffic sign" -> 2.8;
                    case "pole" -> 3.5;
                    default -> 3.0;
                };

                yield new InstanceRule(
                        count,
                        0.5,
                        height,
                        0.5,
                        0.18,
                        0.0,
                        0.0,
                        "#94a3b8",
                        null
                );
            }

            case "sidewalk-tree" -> {
                int count = 20;

                yield new InstanceRule(
                        count,
                        1.2,
                        4.5,
                        1.2,
                        0.28,
                        0.0,
                        0.0,
                        "#65a30d",
                        null
                );
            }

            case "sidewalk" -> {
                if ("person".equals(sourceCategory) || "rider".equals(sourceCategory)) {
                    yield new InstanceRule(
                            3,
                            0.45,
                            1.7,
                            0.45,
                            0.18,
                            0.0,
                            0.0,
                            "#60a5fa",
                            new double[]{-14.0, -2.0, 10.0}
                    );
                }

                /*
                 * 普通 sidewalk 只生成表面，不额外生成模型。
                 */
                yield InstanceRule.empty();
            }

            default -> InstanceRule.empty();
        };
    }

    /**
     * 根据原始识别类别匹配模型库中的模型。
     *
     * 按当前 model_asset.md 调整后的规则：
     *
     * car           -> Vehicle / car，兼容 taxi、rideshare
     * truck         -> Vehicle / truck
     * bus           -> Vehicle / bus
     * train         -> Vehicle / train，如果库中没有则不会生成实例
     * motorcycle    -> 当前库中没有 motorcycle，暂时兜底 Vehicle / car
     * bicycle       -> 当前库中没有 bicycle，默认只生成 bike-lane surface
     * vegetation    -> model_type = Plant
     * pole          -> Props / modern 或 Props / modern_both
     * traffic light -> Props / sign-small 或 Props / sign-large
     * traffic sign  -> Props / wayfinding-small 或 Props / wayfinding-large
     */
    private List<ModelAsset> findCandidateModels(String category, List<ModelAsset> allModels) {
        String normalized = normalize(category);

        if (normalized == null) {
            return Collections.emptyList();
        }

        return allModels.stream()
                .filter(model -> matchesModel(normalized, model))
                .toList();
    }

    /**
     * 判断某个模型是否匹配当前识别类别。
     */
    private boolean matchesModel(String category, ModelAsset model) {
        String modelType = normalize(model.getModelType());
        String modelSubtype = normalize(model.getModelSubtype());

        return switch (category) {
            case "car" ->
                    "vehicle".equals(modelType)
                            && (
                            "car".equals(modelSubtype)
                                    || "taxi".equals(modelSubtype)
                                    || "rideshare".equals(modelSubtype)
                    );

            case "truck" ->
                    "vehicle".equals(modelType)
                            && "truck".equals(modelSubtype);

            case "bus" ->
                    "vehicle".equals(modelType)
                            && "bus".equals(modelSubtype);

            case "train" ->
                    "vehicle".equals(modelType)
                            && "train".equals(modelSubtype);

            case "motorcycle" ->
                /*
                 * 当前数据库中没有 motorcycle 模型。
                 * 为了避免 drive-lane 上完全没有实例，暂时用 Vehicle / car 兜底。
                 * 后续如果加入 motorcycle 模型，只需要改为：
                 * "vehicle".equals(modelType) && "motorcycle".equals(modelSubtype)
                 */
                    "vehicle".equals(modelType)
                            && "car".equals(modelSubtype);

            case "bicycle" ->
                /*
                 * 当前数据库中没有 bicycle / bike 模型。
                 * 这里不强行匹配其他模型，避免 bike-lane 上出现汽车。
                 * 所以如果没有 bicycle 模型，bike-lane 只会显示 surface。
                 */
                    "vehicle".equals(modelType)
                            && (
                            "bicycle".equals(modelSubtype)
                                    || "bike".equals(modelSubtype)
                    );

            case "sidewalk", "person", "rider" ->
                /*
                 * 当前数据库中没有 person 模型。
                 * 后续如果加入人物模型，建议 subtype 使用 person。
                 */
                    "person".equals(modelSubtype);

            case "vegetation" ->
                    "plant".equals(modelType)
                            &&(("big").equals(modelSubtype));

            case "pole" ->
                    "props".equals(modelType)
                            && ("modern_both".equals(modelSubtype));

            case "traffic light" ->
                    "props".equals(modelType)
                            && (
                            "sign-small".equals(modelSubtype)
                                    || "sign-large".equals(modelSubtype)
                    );

            case "traffic sign" ->
                    "props".equals(modelType)
                            && (
                            "wayfinding-small".equals(modelSubtype)
                                    || "wayfinding-large".equals(modelSubtype)
                    );

            default ->
                    category.equals(modelSubtype) || category.equals(modelType);
        };
    }

    /**
     * 判断类别是否应该作为 boundary 处理。
     *
     * 这三类不作为普通道路 segment：
     * - building
     * - wall
     * - fence
     */
    private boolean isBoundaryCategory(String category) {
        return "building".equals(category)
                || "wall".equals(category)
                || "fence".equals(category);
    }

    /**
     * 将 FastAPI 识别出的 boundary 类别转换为数据库中真实存在的模型 subtype。
     *
     * 当前模型库映射规则：
     * - building -> narrow
     * - wall     -> compound-wall
     * - fence    -> fence
     */
    private String normalizeBoundaryModelSubtype(String boundaryType) {
        String normalized = normalize(boundaryType);

        return switch (normalized) {
            case "building" -> "narrow";
            case "wall" -> "compound-wall";
            case "fence" -> "fence";
            default -> normalized;
        };
    }

    /**
     * 根据 boundary 类型匹配模型。
     *
     * 数据库真实映射：
     * - building -> Building / narrow
     * - wall     -> Props / compound-wall
     * - fence    -> Scene / fence
     */
    private List<ModelAsset> findBoundaryCandidateModels(String boundaryType, List<ModelAsset> allModels) {
        String normalized = normalize(boundaryType);
        String targetSubtype = normalizeBoundaryModelSubtype(boundaryType);

        if (normalized == null || targetSubtype == null) {
            return Collections.emptyList();
        }

        return allModels.stream()
                .filter(model -> {
                    String modelType = normalize(model.getModelType());
                    String modelSubtype = normalize(model.getModelSubtype());

                    return switch (normalized) {
                        case "building" ->
                                "building".equals(modelType)
                                        && "narrow".equals(modelSubtype);

                        case "wall" ->
                                "props".equals(modelType)
                                        && "compound-wall".equals(modelSubtype);

                        case "fence" ->
                                "scene".equals(modelType)
                                        && "fence".equals(modelSubtype);

                        default ->
                                targetSubtype.equals(modelSubtype);
                    };
                })
                .toList();
    }

    /**
     * 决定左右 boundary 类型。
     *
     * 如果识别结果中没有 building / wall / fence，则默认使用 building。
     * left 使用第一个 boundary 类型；
     * right 使用最后一个 boundary 类型。
     */
    private String resolveBoundaryTypeForSide(List<String> boundaryTypes, String side) {
        if (boundaryTypes == null || boundaryTypes.isEmpty()) {
            return "building";
        }

        if ("left".equalsIgnoreCase(side)) {
            return boundaryTypes.get(0);
        }

        return boundaryTypes.get(boundaryTypes.size() - 1);
    }

    /**
     * 决定 boundary 上生成多少个模型实例。
     *
     * building 实际使用 narrow，模型偏窄，可以数量多一些；
     * wall 实际使用 compound-wall；
     * fence 实际使用 fence。
     */
    private int resolveBoundaryTargetCount(String type, double roadLength) {
        String normalized = normalize(type);

        return switch (normalized) {
            case "building" -> 7;
            case "wall" -> 10;
            case "fence" -> 6;
            default -> Math.max(2, (int) Math.round(roadLength / 24.0));
        };
    }

    /**
     * 决定不同 boundary 类型的空间参数。
     *
     * 这里传入的仍然是 FastAPI 类别：
     * - building
     * - wall
     * - fence
     *
     * 但实际模型映射为：
     * - building -> narrow
     * - wall     -> compound-wall
     * - fence    -> fence
     */
    private BoundaryPlacement resolveBoundaryPlacement(String type) {
        String normalized = normalize(type);

        return switch (normalized) {
            case "building" -> new BoundaryPlacement(
                    5.0,    // footprintWidth：窄体建筑占地宽度
                    6.0,    // boxHeight：支撑高度
                    4.0,    // roadGap：建筑与道路间距
                    0.0,    // modelYOffset
                    0.0,    // modelOffsetX
                    0.0,    // modelOffsetZ
                    5.0,    // modelWidth
                    8.0,    // modelHeight
                    8.0     // modelDepth
            );

            case "wall" -> new BoundaryPlacement(
                    2.0,    // footprintWidth：围墙较窄
                    2.0,    // boxHeight
                    0.6,    // roadGap：贴近道路
                    0.0,
                    0.0,
                    0.0,
                    2.0,
                    3.0,
                    3.0
            );

            case "fence" -> new BoundaryPlacement(
                    1.2,    // footprintWidth：围栏最窄
                    1.5,    // boxHeight
                    0.4,    // roadGap：更贴近道路
                    0.0,
                    0.0,
                    0.0,
                    1.2,
                    2.0,
                    2.0
            );

            default -> new BoundaryPlacement(
                    5.0,
                    5.0,
                    2.0,
                    0.0,
                    0.0,
                    0.0,
                    4.0,
                    5.0,
                    6.0
            );
        };
    }

    /**
     * 根据左右侧决定 boundary 模型朝向。
     */
    private SceneVector3DTO resolveBoundaryRotation(String type, String side) {
        boolean left = "left".equalsIgnoreCase(side);
        String normalized = normalize(type);

        return switch (normalized) {
            case "building" -> left
                    ? new SceneVector3DTO(0, -90, 0)
                    : new SceneVector3DTO(0, 90, 0);

            case "wall", "fence" -> left
                    ? new SceneVector3DTO(0, 90, 0)
                    : new SceneVector3DTO(0, -90, 0);

            default -> new SceneVector3DTO(0, 0, 0);
        };
    }

    /**
     * 从候选模型中随机选择 targetCount 个模型。
     *
     * 如果候选模型数量不足，则循环复用已有模型。
     */
    private List<ModelAsset> pickRandomModels(List<ModelAsset> candidates, int targetCount) {
        List<ModelAsset> results = new ArrayList<>();

        if (candidates == null || candidates.isEmpty() || targetCount <= 0) {
            return results;
        }

        List<ModelAsset> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);

        int index = 0;

        while (results.size() < targetCount) {
            results.add(shuffled.get(index % shuffled.size()));
            index++;
        }

        return results;
    }

    /**
     * 调用 LayoutGA 优化 Z 轴位置。
     *
     * 如果 GA 执行失败，则回退到默认 Z 坐标或均匀分布坐标。
     */
    private double[] optimizeZPositions(
            int count,
            double roadLength,
            double edgePadding,
            double[] modelDepths,
            double[] fallback
    ) {
        if (count <= 0) {
            return new double[0];
        }

        if (count == 1) {
            return new double[]{0.0};
        }

        try {
            return LayoutGA.optimizeZPositionsGA(
                    count,
                    roadLength,
                    edgePadding,
                    modelDepths,
                    GA_GENERATIONS,
                    GA_POPULATION_SIZE
            );
        } catch (Exception e) {
            System.err.println("GA Z轴优化失败，使用默认位置: " + e.getMessage());
            return buildFallbackZ(count, roadLength, fallback);
        }
    }

    /**
     * 当 GA 优化失败时，使用默认 Z 坐标或均匀分布坐标。
     */
    private double[] buildFallbackZ(int count, double roadLength, double[] fallback) {
        if (fallback != null && fallback.length >= count) {
            return Arrays.copyOf(fallback, count);
        }

        double[] result = new double[count];

        if (count == 1) {
            result[0] = 0.0;
            return result;
        }

        double start = -roadLength / 2.0 + 6.0;
        double end = roadLength / 2.0 - 6.0;
        double step = (end - start) / (count - 1);

        for (int i = 0; i < count; i++) {
            result[i] = start + step * i;
        }

        return result;
    }

    /**
     * 根据 segment 类型决定 surface 颜色。
     */
    private String resolveSegmentColor(String type) {
        return switch (type) {
            case "sidewalk" -> "#d1d5db";
            case "sidewalk-tree" -> "#84cc16";
            case "sidewalk-modern" -> "#cbd5e1";
            case "drive-lane" -> "#4b5563";
            case "bus-lane" -> "#b91c1c";
            case "bike-lane" -> "#15803d";
            case "parking-lane" -> "#6b7280";
            case "temporary" -> "#ffffff";
            case "flex-zone" -> "#9ca3af";
            default -> "#9ca3af";
        };
    }

    /**
     * 根据 segment 类型决定前端材质 key。
     *
     * 如果前端暂时没有 bike-lane / sidewalk-modern 的专门材质，
     * 也不会影响渲染，因为 surface 同时设置了 color。
     */
    private String resolveSegmentMaterialKey(String type) {
        return switch (type) {
            case "sidewalk", "sidewalk-tree", "sidewalk-modern" -> "sidewalk";
            case "drive-lane" -> "drive-lane";
            case "bus-lane" -> "surface-red bus-lane";
            case "bike-lane" -> "bike-lane";
            case "parking-lane" -> "parking-lane";
            case "temporary" -> "temporary";
            case "flex-zone" -> "flex-zone";
            default -> type;
        };
    }

    /**
     * 字符串归一化。
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase();
    }

    /**
     * 避免 Double 空指针。
     */
    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    /**
     * 调用 FastAPI 模型服务。
     *
     * FastAPI 接口：
     * POST http://localhost:8000/predict
     *
     * 请求参数：
     * multipart/form-data
     * file: 图片文件
     *
     * 返回值：
     * List<String>
     */
    private List<String> callMask2FormerAPI(MultipartFile file) throws IOException {
        String url = "http://localhost:8000/predict";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        Resource fileAsResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        body.add("file", fileAsResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url,
                requestEntity,
                String.class
        );

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new IOException("FastAPI 返回错误: " + response.getStatusCode());
        }

        return objectMapper.readValue(
                response.getBody(),
                new TypeReference<List<String>>() {}
        );
    }

    /**
     * 预测 segment 规格。
     *
     * segmentType：
     * StreetViz3D 内部用于渲染的 segment 类型。
     *
     * sourceCategory：
     * FastAPI 原始识别类别。
     *
     * width：
     * 该 segment 的原始宽度，后续会乘 DEFAULT_WIDTH_SCALE 得到 renderedWidth。
     */
    private static class PredictSegmentSpec {
        private final String segmentType;
        private final String sourceCategory;
        private final double width;

        private PredictSegmentSpec(String segmentType, String sourceCategory, double width) {
            this.segmentType = segmentType;
            this.sourceCategory = sourceCategory;
            this.width = width;
        }
    }

    /**
     * segment 内模型实例生成规则。
     */
    private static class InstanceRule {
        private final int count;
        private final double width;
        private final double height;
        private final double depth;
        private final double y;
        private final double offsetX;
        private final double rotationY;
        private final String color;
        private final double[] defaultZ;

        private InstanceRule(
                int count,
                double width,
                double height,
                double depth,
                double y,
                double offsetX,
                double rotationY,
                String color,
                double[] defaultZ
        ) {
            this.count = count;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.y = y;
            this.offsetX = offsetX;
            this.rotationY = rotationY;
            this.color = color;
            this.defaultZ = defaultZ;
        }

        private static InstanceRule empty() {
            return new InstanceRule(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "#ffffff",
                    null
            );
        }
    }

    /**
     * boundary 空间布局参数。
     */
    private static class BoundaryPlacement {
        private final double footprintWidth;
        private final double boxHeight;
        private final double roadGap;
        private final double modelYOffset;
        private final double modelOffsetX;
        private final double modelOffsetZ;
        private final double modelWidth;
        private final double modelHeight;
        private final double modelDepth;
        private final double footprintDepth;

        private BoundaryPlacement(
                double footprintWidth,
                double boxHeight,
                double roadGap,
                double modelYOffset,
                double modelOffsetX,
                double modelOffsetZ,
                double modelWidth,
                double modelHeight,
                double modelDepth
        ) {
            this.footprintWidth = footprintWidth;
            this.boxHeight = boxHeight;
            this.roadGap = roadGap;
            this.modelYOffset = modelYOffset;
            this.modelOffsetX = modelOffsetX;
            this.modelOffsetZ = modelOffsetZ;
            this.modelWidth = modelWidth;
            this.modelHeight = modelHeight;
            this.modelDepth = modelDepth;
            this.footprintDepth = Math.max(30.0, modelDepth);
        }
    }
}