package com.streetviz3d.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.streetviz3d.backend.dto.response.StreetPreviewResponse;
import com.streetviz3d.backend.dto.scene.StreetSceneDTO;
import com.streetviz3d.backend.dto.street.BoundaryPreviewDTO;
import com.streetviz3d.backend.dto.street.SegmentPreviewDTO;
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

    /**
     * 对外主方法：
     * Streetmix URL -> StreetPreviewResponse -> StreetSceneDTO
     */
    public StreetSceneDTO buildStreetSceneFromUrl(String streetmixUrl) {
        StreetPreviewResponse preview = buildStreetPreviewFromUrl(streetmixUrl);
        return streetSceneLayoutService.buildStreetScene(preview);
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
        response.setStreetName(readText(streetNode, "name", "streetName", "title", "slug"));
        response.setCreatorId(ref.creatorId);
        response.setNamespacedId(parseInteger(ref.namespacedId));
        response.setUnit(readInteger(streetNode, "unit"));
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
     * 解析 Streetmix URL
     * 例如：
     * https://streetmix.net/erq040609/11/streetmix-3d-example-street
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
     * 调用 Streetmix API
     */
    private JsonNode fetchStreetmixStreet(StreetmixRef ref) {
        String apiUrl = "https://streetmix.net/api/v1/streets?namespacedId="
                + URLEncoder.encode(ref.namespacedId, StandardCharsets.UTF_8)
                + "&creatorId="
                + URLEncoder.encode(ref.creatorId, StandardCharsets.UTF_8);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
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

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Streetmix API 请求失败，HTTP 状态码：" + response.statusCode());
            }

            return objectMapper.readTree(response.body());
        } catch (Exception e) {
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

            SegmentPreviewDTO dto = new SegmentPreviewDTO();
            dto.setSegmentId("streetmix-segment-" + sortIndex + "-" + UUID.randomUUID().toString().substring(0, 8));
            dto.setSortIndex(sortIndex);
            dto.setType(type);
            dto.setWidth(readDouble(segmentNode, "width"));
            dto.setElevation(readDouble(segmentNode, "elevation"));
            dto.setSlopeOn(readBoolean(segmentNode, "slopeOn"));
            dto.setSlopeValues(findNode(segmentNode, "slopeValues"));
            dto.setVariantData(buildVariantData(type, segmentNode));
            dto.setModel(null);

            result.add(dto);
            sortIndex++;
        }

        return result;
    }

    /**
     * 当前先不从 Streetmix 构建 boundary
     * 第一版先保证 segment 主链路跑通
     */
    private StreetPreviewResponse.BoundaryMap buildBoundaries(JsonNode streetNode) {
        StreetPreviewResponse.BoundaryMap map = new StreetPreviewResponse.BoundaryMap();
        map.setLeft(null);
        map.setRight(null);

        // 如果你后面确认了 Streetmix 里的边界字段，再在这里补 left/right
        return map;
    }

    /**
     * 关键点：
     * 这里不是要完美还原 Streetmix 全部字段，
     * 而是要尽量补齐 StreetSceneLayoutService 当前真正依赖的 variantData 字段
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
            fillIfMissing(node, "treeType", "tree");
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