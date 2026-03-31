package com.streetviz3d.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streetviz3d.backend.dto.street.BoundaryPreviewDTO;
import com.streetviz3d.backend.dto.model.ModelAssetDTO;
import com.streetviz3d.backend.dto.street.SegmentPreviewDTO;
import com.streetviz3d.backend.dto.response.StreetPreviewResponse;
import com.streetviz3d.backend.entity.Boundary;
import com.streetviz3d.backend.entity.ModelAsset;
import com.streetviz3d.backend.entity.Segment;
import com.streetviz3d.backend.entity.Street;
import com.streetviz3d.backend.mapper.BoundaryMapper;
import com.streetviz3d.backend.mapper.ModelAssetMapper;
import com.streetviz3d.backend.mapper.SegmentMapper;
import com.streetviz3d.backend.mapper.StreetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StreetPreviewService {

    private final StreetMapper streetMapper;
    private final SegmentMapper segmentMapper;
    private final BoundaryMapper boundaryMapper;
    private final ModelAssetMapper modelAssetMapper;
    private final ObjectMapper objectMapper;

    public StreetPreviewResponse getStreetPreview(String streetId) {
        Street street = streetMapper.selectById(streetId);
        if (street == null) {
            throw new RuntimeException("街道不存在: " + streetId);
        }

        List<Segment> segments = segmentMapper.selectList(
                new LambdaQueryWrapper<Segment>()
                        .eq(Segment::getStreetId, streetId)
                        .orderByAsc(Segment::getSortIndex)
        );

        List<Boundary> boundaries = boundaryMapper.selectList(
                new LambdaQueryWrapper<Boundary>()
                        .eq(Boundary::getStreetId, streetId)
        );

        Set<String> modelIds = new HashSet<>();
        for (Segment segment : segments) {
            if (segment.getModelId() != null && !segment.getModelId().isBlank()) {
                modelIds.add(segment.getModelId());
            }
        }
        for (Boundary boundary : boundaries) {
            if (boundary.getModelId() != null && !boundary.getModelId().isBlank()) {
                modelIds.add(boundary.getModelId());
            }
        }

        Map<String, ModelAsset> modelMap = modelIds.isEmpty()
                ? Collections.emptyMap()
                : modelAssetMapper.selectBatchIds(modelIds).stream()
                .collect(Collectors.toMap(ModelAsset::getModelId, m -> m));

        StreetPreviewResponse response = new StreetPreviewResponse();
        response.setStreetId(street.getStreetId());
        response.setStreetName(street.getStreetName());
        response.setCreatorId(street.getCreatorId());
        response.setNamespacedId(street.getNamespacedId());
        response.setUnit(street.getUnit());
        response.setSchemaVersion(street.getSchemaVersion());
        response.setWidth(street.getWidth());
        response.setSkybox(street.getSkybox());
        response.setWeather(street.getWeather());
        response.setLocation(street.getLocation());
        response.setEditCount(street.getEditCount());

        List<SegmentPreviewDTO> segmentDTOList = segments.stream()
                .map(segment -> toSegmentDTO(segment, modelMap.get(segment.getModelId())))
                .toList();
        response.setSegments(segmentDTOList);

        StreetPreviewResponse.BoundaryMap boundaryMap = new StreetPreviewResponse.BoundaryMap();
        for (Boundary boundary : boundaries) {
            BoundaryPreviewDTO dto = toBoundaryDTO(boundary, modelMap.get(boundary.getModelId()));
            if ("left".equalsIgnoreCase(boundary.getSide())) {
                boundaryMap.setLeft(dto);
            } else if ("right".equalsIgnoreCase(boundary.getSide())) {
                boundaryMap.setRight(dto);
            }
        }
        response.setBoundaries(boundaryMap);

        return response;
    }

    private SegmentPreviewDTO toSegmentDTO(Segment segment, ModelAsset modelAsset) {
        SegmentPreviewDTO dto = new SegmentPreviewDTO();
        dto.setSegmentId(segment.getSegmentId());
        dto.setSortIndex(segment.getSortIndex());
        dto.setType(segment.getSegmentType());
        dto.setWidth(segment.getWidth());
        dto.setElevation(segment.getElevation());
        dto.setSlopeOn(segment.getSlopeOn());
        dto.setSlopeValues(parseJson(segment.getSlopeValues()));
        dto.setVariantData(parseJson(segment.getVariantData()));
        dto.setModel(toModelDTO(modelAsset));
        return dto;
    }

    private BoundaryPreviewDTO toBoundaryDTO(Boundary boundary, ModelAsset modelAsset) {
        BoundaryPreviewDTO dto = new BoundaryPreviewDTO();
        dto.setBoundaryId(boundary.getBoundaryId());
        dto.setSide(boundary.getSide());
        dto.setType(boundary.getBoundaryType());
        dto.setFloors(boundary.getFloors());
        dto.setElevation(boundary.getElevation());
        dto.setVariantData(parseJson(boundary.getVariantData()));
        dto.setModel(toModelDTO(modelAsset));
        return dto;
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

    private JsonNode parseJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败: " + json, e);
        }
    }
}