package com.streetviz3d.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.streetviz3d.backend.dto.model.ModelAssetDTO;
import com.streetviz3d.backend.dto.response.StreetPreviewResponse;
import com.streetviz3d.backend.dto.scene.StreetSceneDTO;
import com.streetviz3d.backend.dto.street.BoundaryPreviewDTO;
import com.streetviz3d.backend.dto.street.SegmentPreviewDTO;
import com.streetviz3d.backend.entity.ModelAsset;
import com.streetviz3d.backend.mapper.ModelAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StreetmixService {

    private static final Pattern STREETMIX_URL_PATTERN =
            Pattern.compile(
                    "https?://streetmix\\.net/([^/]+)/([^/?#]+)(?:/[^?#]+)?/?(?:\\?.*)?",
                    Pattern.CASE_INSENSITIVE
            );

    private final ObjectMapper objectMapper;
    private final StreetSceneLayoutService streetSceneLayoutService;
    private final ModelAssetMapper modelAssetMapper;

    /**
     * 对外主方法：
     * Streetmix URL -> StreetPreviewResponse -> StreetSceneDTO
     */
    public StreetSceneDTO buildStreetSceneFromUrl(String streetmixUrl) {
        StreetPreviewResponse preview = buildStreetPreviewFromUrl(streetmixUrl);
        System.out.println("Streetmix preview = " + preview);

        StreetSceneDTO scene = streetSceneLayoutService.buildStreetScene(preview);
        System.out.println("Streetmix scene = " + scene);

        return scene;
    }

    /**
     * Streetmix URL -> StreetPreviewResponse
     */
    public StreetPreviewResponse buildStreetPreviewFromUrl(String streetmixUrl) {
        StreetmixRef ref = parseStreetmixUrl(streetmixUrl);
        JsonNode root = fetchStreetmixStreet(ref);
        JsonNode streetNode = unwrapStreetNode(root);

        if (streetNode == null || streetNode.isNull()) {
            throw new RuntimeException("未从 Streetmix API 中解析到街道数据");
        }

        StreetPreviewResponse response = new StreetPreviewResponse();
        response.setStreetId("streetmix-" + ref.creatorId + "-" + ref.namespacedId);
        response.setStreetName(readText(root, "name", "streetName", "title", "slug"));
        response.setCreatorId(ref.creatorId);
        response.setNamespacedId(parseInteger(ref.namespacedId));
        response.setUnit(readInteger(streetNode, "unit", "units"));
        response.setSchemaVersion(readInteger(streetNode, "schemaVersion"));
        response.setWidth(readDouble(streetNode, "width"));
        response.setSkybox(readText(streetNode, "skybox"));
        response.setWeather(readText(streetNode, "weather"));
        response.setLocation(readText(streetNode, "location"));
        response.setEditCount(readInteger(streetNode, "editCount"));

        List<SegmentPreviewDTO> segments = buildSegments(streetNode);
        response.setSegments(segments);

        if (response.getWidth() == null) {
            double totalWidth = 0.0;
            for (SegmentPreviewDTO segment : segments) {
                totalWidth += segment.getWidth() == null ? 0.0 : segment.getWidth();
            }
            response.setWidth(totalWidth);
        }

        response.setBoundaries(buildBoundaries(streetNode));
        return response;
    }

    /**
     * 解析 Streetmix 分享 URL
     * 例如：
     * https://streetmix.net/erq040609/12/laoesecondstreet
     */
    private StreetmixRef parseStreetmixUrl(String streetmixUrl) {
        if (streetmixUrl == null || streetmixUrl.isBlank()) {
            throw new RuntimeException("Streetmix URL 不能为空");
        }

        Matcher matcher = STREETMIX_URL_PATTERN.matcher(streetmixUrl.trim());
        if (!matcher.matches()) {
            throw new RuntimeException("Streetmix URL 格式不正确");
        }

        return new StreetmixRef(matcher.group(1), matcher.group(2));
    }

    /**
     * 调用 Streetmix 查询接口：
     * https://streetmix.net/api/v1/streets?namespacedId=...&creatorId=...
     *
     * 通过自动重定向跳到真正的 /api/v1/streets/{uuid}
     */
    private JsonNode fetchStreetmixStreet(StreetmixRef ref) {
        String apiUrl = "https://streetmix.net/api/v1/streets?namespacedId="
                + URLEncoder.encode(ref.namespacedId, StandardCharsets.UTF_8)
                + "&creatorId="
                + URLEncoder.encode(ref.creatorId, StandardCharsets.UTF_8);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "StreetViz3D/1.0")
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            System.out.println("Streetmix query URL = " + apiUrl);
            System.out.println("Streetmix status = " + response.statusCode());
            System.out.println("Streetmix final URI = " + response.uri());
            System.out.println("Streetmix body = " + response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Streetmix API 请求失败，HTTP 状态码：" + response.statusCode());
            }

            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("获取 Streetmix 街道数据失败", e);
        }
    }

    /**
     * 兼容 Streetmix API 返回格式
     */
    private JsonNode unwrapStreetNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        if (root.isArray()) {
            return root.isEmpty() ? null : root.get(0);
        }

        if (root.has("data") && root.get("data").isArray()) {
            JsonNode data = root.get("data");
            return data.isEmpty() ? null : data.get(0);
        }

        if (root.has("data") && root.get("data").has("street")) {
            return root.get("data").get("street");
        }

        if (root.has("street")) {
            return root.get("street");
        }

        return root;
    }

    /**
     * 构建 segments
     */
    private List<SegmentPreviewDTO> buildSegments(JsonNode streetNode) {
        List<SegmentPreviewDTO> result = new ArrayList<>();

        JsonNode segmentsNode = streetNode.get("segments");
        if (segmentsNode == null || !segmentsNode.isArray()) {
            return result;
        }

        int sortIndex = 0;
        for (JsonNode segmentNode : segmentsNode) {
            String type = readText(segmentNode, "type", "segmentType");
            if (type == null || type.isBlank()) {
                continue;
            }

            JsonNode slopeNode = findNode(segmentNode, "slope");

            SegmentPreviewDTO dto = new SegmentPreviewDTO();
            dto.setSegmentId("streetmix-segment-" + sortIndex + "-" + UUID.randomUUID().toString().substring(0, 8));
            dto.setSortIndex(sortIndex);
            dto.setType(type);
            dto.setWidth(readDouble(segmentNode, "width"));
            dto.setElevation(readDouble(segmentNode, "elevation"));
            dto.setSlopeOn(
                    slopeNode != null ? readBoolean(slopeNode, "on") : readBoolean(segmentNode, "slopeOn")
            );
            dto.setSlopeValues(
                    slopeNode != null ? findNode(slopeNode, "values") : findNode(segmentNode, "slopeValues")
            );
            dto.setVariantData(buildVariantData(type, segmentNode));
            dto.setModel(resolveSegmentModel(type, dto.getVariantData()));

            result.add(dto);
            sortIndex++;
        }

        return result;
    }

    /**
     * 构建 boundary
     */
    private StreetPreviewResponse.BoundaryMap buildBoundaries(JsonNode streetNode) {
        StreetPreviewResponse.BoundaryMap map = new StreetPreviewResponse.BoundaryMap();

        JsonNode boundaryNode = findNode(streetNode, "boundary");
        if (boundaryNode == null || boundaryNode.isNull()) {
            map.setLeft(null);
            map.setRight(null);
            return map;
        }

        JsonNode leftNode = findNode(boundaryNode, "left");
        if (leftNode != null && !leftNode.isNull()) {
            BoundaryPreviewDTO left = new BoundaryPreviewDTO();
            left.setBoundaryId(readText(leftNode, "id"));
            left.setSide("left");
            left.setType(mapBoundaryType(readText(leftNode, "variant")));
            left.setFloors(readInteger(leftNode, "floors"));
            left.setElevation(readDouble(leftNode, "elevation"));
            left.setVariantData(leftNode.isObject() ? leftNode : null);
            left.setModel(resolveBoundaryModel(left.getType(), left.getVariantData()));
            map.setLeft(left);
        } else {
            map.setLeft(null);
        }

        JsonNode rightNode = findNode(boundaryNode, "right");
        if (rightNode != null && !rightNode.isNull()) {
            BoundaryPreviewDTO right = new BoundaryPreviewDTO();
            right.setBoundaryId(readText(rightNode, "id"));
            right.setSide("right");
            right.setType(mapBoundaryType(readText(rightNode, "variant")));
            right.setFloors(readInteger(rightNode, "floors"));
            right.setElevation(readDouble(rightNode, "elevation"));
            right.setVariantData(rightNode.isObject() ? rightNode : null);
            right.setModel(resolveBoundaryModel(right.getType(), right.getVariantData()));
            map.setRight(right);
        } else {
            map.setRight(null);
        }

        return map;
    }

    /**
     * 尽量补齐 StreetSceneLayoutService 当前真正依赖的 variantData 字段
     */
    private JsonNode buildVariantData(String type, JsonNode segmentNode) {
        ObjectNode node = objectMapper.createObjectNode();

        JsonNode rawVariantData = findNode(segmentNode, "variantData");
        if (rawVariantData != null && rawVariantData.isObject()) {
            node.setAll((ObjectNode) rawVariantData);
        }

        String variantString = readText(segmentNode, "variantString", "variantName", "variant");
        if (variantString != null && !variantString.isBlank()) {
            node.put("rawVariantString", variantString);
        }

        String lowerType = type.toLowerCase(Locale.ROOT);
        String lowerVariant = variantString == null ? "" : variantString.toLowerCase(Locale.ROOT);

        if ("drive-lane".equals(lowerType)) {
            fillIfMissing(node, "vehicleType",
                    lowerVariant.contains("bus") ? "bus" :
                            lowerVariant.contains("truck") ? "truck" :
                                    lowerVariant.contains("taxi") ? "taxi" : "car");
            fillIfMissing(node, "flowDirection",
                    lowerVariant.contains("inbound") ? "inbound" : "outbound");
        } else if ("bus-lane".equals(lowerType)) {
            fillIfMissing(node, "flowDirection",
                    lowerVariant.contains("inbound") ? "inbound" : "outbound");
            fillIfMissing(node, "laneStyle",
                    (lowerVariant.contains("red") || lowerVariant.contains("colored")) ? "colored" : "regular");
        } else if ("parking-lane".equals(lowerType)) {
            fillIfMissing(node, "parkingDirection",
                    lowerVariant.contains("inbound") ? "inbound" : "outbound");
            fillIfMissing(node, "placementSide",
                    lowerVariant.contains("left") ? "left" : "right");
        } else if ("flex-zone".equals(lowerType)) {
            fillIfMissing(node, "flowDirection",
                    lowerVariant.contains("inbound") ? "inbound" : "outbound");
            fillIfMissing(node, "placementSide",
                    lowerVariant.contains("left") ? "left" : "right");
        } else if ("temporary".equals(lowerType)) {
            fillIfMissing(node, "barrierType", "traffic-cone");
        } else if ("sidewalk-tree".equals(lowerType)) {
            if (lowerVariant.contains("palm")) {
                fillIfMissing(node, "treeType", "palm-tree");
            } else if (lowerVariant.contains("big")) {
                fillIfMissing(node, "treeType", "big");
            } else {
                fillIfMissing(node, "treeType", "big");
            }
        } else if ("sidewalk".equals(lowerType)) {
            if (lowerVariant.contains("dense")) {
                fillIfMissing(node, "pedestrianDensity", "dense");
            } else if (lowerVariant.contains("sparse")) {
                fillIfMissing(node, "pedestrianDensity", "sparse");
            } else if (lowerVariant.contains("empty")) {
                fillIfMissing(node, "pedestrianDensity", "empty");
            } else {
                fillIfMissing(node, "pedestrianDensity", "normal");
            }
        }

        return node;
    }

    /**
     * Segment -> model_asset 映射
     */
    private ModelAssetDTO resolveSegmentModel(String type, JsonNode variantData) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String normalizedType = type.toLowerCase(Locale.ROOT);
        ModelAsset model = null;

        switch (normalizedType) {
            case "drive-lane" -> {
                String vehicleType = readText(variantData, "vehicleType");
                model = findFirstModel("Vehicle", vehicleType);
                if (model == null) {
                    model = findFirstModelByType("Vehicle");
                }
            }
            case "bus-lane" -> {
                model = findFirstModel("Vehicle", "bus");
                if (model == null) {
                    model = findFirstModelByType("Vehicle");
                }
            }
            case "parking-lane" -> {
                model = findFirstModel("Vehicle", "car");
                if (model == null) {
                    model = findFirstModelByType("Vehicle");
                }
            }
            case "flex-zone" -> {
                model = findFirstModel("Vehicle", "taxi");
                if (model == null) {
                    model = findFirstModelByType("Vehicle");
                }
            }
            case "temporary" -> {
                String barrierType = readText(variantData, "barrierType");
                model = findFirstModel("Props", barrierType);
                if (model == null) {
                    model = findFirstModelByType("Props");
                }
            }
            case "sidewalk-tree" -> {
                String treeType = readText(variantData, "treeType");
                model = findFirstModel("Plant", treeType);
                if (model == null) {
                    model = findFirstModelByType("Plant");
                }
            }
            case "sidewalk" -> {
                return null;
            }
            default -> {
                return null;
            }
        }

        return toModelDTO(model);
    }

    /**
     * Boundary -> model_asset 映射
     */
    private ModelAssetDTO resolveBoundaryModel(String boundaryType, JsonNode variantData) {
        if (boundaryType == null || boundaryType.isBlank()) {
            return null;
        }

        String normalizedType = boundaryType.trim().toLowerCase(Locale.ROOT);
        ModelAsset model = null;

        switch (normalizedType) {
            case "parking-lot" -> {
                model = findFirstModel("Scene", "parking-lot");
                if (model == null) {
                    model = findFirstModelByType("Scene");
                }
            }

            case "waterfront" -> {
                model = findFirstModel("Scene", "waterfront");
                if (model == null) {
                    model = findFirstModelByType("Scene");
                }
            }

            case "residential" -> {
                model = findFirstModel("Building", "residential");
                if (model == null) {
                    model = findFirstModel("Building", "wide");
                }
                if (model == null) {
                    model = findFirstModel("Building", "narrow");
                }
                if (model == null) {
                    model = findFirstModelByType("Building");
                }
            }

            case "narrow" -> {
                model = findFirstModel("Building", "narrow");
                if (model == null) {
                    model = findFirstModel("Building", "residential");
                }
                if (model == null) {
                    model = findFirstModelByType("Building");
                }
            }

            case "wide" -> {
                model = findFirstModel("Building", "wide");
                if (model == null) {
                    model = findFirstModel("Building", "residential");
                }
                if (model == null) {
                    model = findFirstModelByType("Building");
                }
            }

            case "arcade" -> {
                model = findFirstModel("Building", "arcade");
                if (model == null) {
                    model = findFirstModel("Building", "residential");
                }
                if (model == null) {
                    model = findFirstModelByType("Building");
                }
            }

            case "fence" -> {
                model = findFirstModel("Props", "fence");
                if (model == null) {
                    model = findFirstModel("Scene", "fence");
                }
                if (model == null) {
                    model = findFirstModelByType("Props");
                }
            }

            case "compound-wall" -> {
                model = findFirstModel("Props", "compound-wall");
                if (model == null) {
                    model = findFirstModel("Props", "wall");
                }
                if (model == null) {
                    model = findFirstModel("Scene", "compound-wall");
                }
                if (model == null) {
                    model = findFirstModelByType("Props");
                }
            }

            case "grass" -> {
                model = findFirstModel("Scene", "grass");
                if (model == null) {
                    model = findFirstModel("Plant", "grass");
                }
                if (model == null) {
                    model = findFirstModelByType("Scene");
                }
            }

            default -> {
                String rawVariant = readText(variantData, "variant");
                if (rawVariant != null && !rawVariant.isBlank()) {
                    model = findFirstModel("Building", rawVariant);
                    if (model == null) {
                        model = findFirstModel("Props", rawVariant);
                    }
                    if (model == null) {
                        model = findFirstModel("Scene", rawVariant);
                    }
                }
            }
        }

        return toModelDTO(model);
    }

    private ModelAsset findFirstModel(String modelType, String modelSubtype) {
        LambdaQueryWrapper<ModelAsset> wrapper = new LambdaQueryWrapper<ModelAsset>()
                .eq(ModelAsset::getModelType, modelType);

        if (modelSubtype != null && !modelSubtype.isBlank()) {
            wrapper.eq(ModelAsset::getModelSubtype, modelSubtype);
        }

        wrapper.last("limit 1");
        List<ModelAsset> list = modelAssetMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    private ModelAsset findFirstModelByType(String modelType) {
        LambdaQueryWrapper<ModelAsset> wrapper = new LambdaQueryWrapper<ModelAsset>()
                .eq(ModelAsset::getModelType, modelType)
                .last("limit 1");

        List<ModelAsset> list = modelAssetMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    private ModelAssetDTO toModelDTO(ModelAsset modelAsset) {
        if (modelAsset == null) {
            return null;
        }

        ModelAssetDTO dto = new ModelAssetDTO();
        dto.setModelId(modelAsset.getModelId());
        dto.setModelName(modelAsset.getModelName());
        dto.setModelType(modelAsset.getModelType());
        dto.setModelSubtype(modelAsset.getModelSubtype());
        dto.setDisplayName(modelAsset.getDisplayName());
        dto.setModelUrl(modelAsset.getModelUrl());
        dto.setPreviewUrl(modelAsset.getModelPreviewUrl());
        return dto;
    }

    private String mapBoundaryType(String variant) {
        if (variant == null || variant.isBlank()) {
            return "residential";
        }

        String lower = variant.trim().toLowerCase(Locale.ROOT);

        return switch (lower) {
            case "residential" -> "residential";
            case "narrow" -> "narrow";
            case "wide" -> "wide";
            case "arcade" -> "arcade";
            case "fence" -> "fence";
            case "grass" -> "grass";
            case "compound-wall" -> "compound-wall";
            case "parking-lot" -> "parking-lot";
            case "waterfront" -> "waterfront";
            default -> {
                if (lower.contains("parking")) {
                    yield "parking-lot";
                }
                if (lower.contains("water")) {
                    yield "waterfront";
                }
                if (lower.contains("compound") && lower.contains("wall")) {
                    yield "compound-wall";
                }
                yield lower;
            }
        };
    }

    private void fillIfMissing(ObjectNode node, String key, String fallbackValue) {
        if (!node.has(key) || node.get(key).isNull() || node.get(key).asText().isBlank()) {
            node.put(key, fallbackValue);
        }
    }

    private JsonNode findNode(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String... keys) {
        JsonNode value = findNode(node, keys);
        if (value == null) {
            return null;
        }
        String text = value.asText();
        return (text == null || text.isBlank()) ? null : text;
    }

    private Integer readInteger(JsonNode node, String... keys) {
        JsonNode value = findNode(node, keys);
        if (value == null) {
            return null;
        }

        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }

        try {
            return Integer.parseInt(value.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double readDouble(JsonNode node, String... keys) {
        JsonNode value = findNode(node, keys);
        if (value == null) {
            return null;
        }

        if (value.isNumber()) {
            return value.asDouble();
        }

        try {
            return Double.parseDouble(value.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean readBoolean(JsonNode node, String... keys) {
        JsonNode value = findNode(node, keys);
        if (value == null) {
            return null;
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        String text = value.asText();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private record StreetmixRef(String creatorId, String namespacedId) {
    }
}