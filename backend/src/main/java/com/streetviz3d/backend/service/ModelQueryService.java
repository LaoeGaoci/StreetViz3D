package com.streetviz3d.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.streetviz3d.backend.dto.ModelPreviewDTO;
import com.streetviz3d.backend.entity.ModelAsset;
import com.streetviz3d.backend.mapper.ModelAssetMapper;
import com.streetviz3d.backend.config.NginxProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor

@Service
public class ModelQueryService {

    private final ModelAssetMapper modelAssetMapper;
    private final NginxProperties nginxProperties;

    private String buildPreviewUrl(String modelType, String previewFileName) {
        if (modelType == null || modelType.isBlank()
                || previewFileName == null || previewFileName.isBlank()) {
            return "";
        }

        String baseUrl = nginxProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }

        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        return normalizedBaseUrl + "/model/Preview/" + modelType + "/" + previewFileName;
    }

    public List<ModelPreviewDTO> getModelsByType(String type) {
        LambdaQueryWrapper<ModelAsset> wrapper = Wrappers.<ModelAsset>lambdaQuery()
                .eq(ModelAsset::getModelType, type)
                .orderByAsc(ModelAsset::getModelName);

        return modelAssetMapper.selectList(wrapper)
                .stream()
                .map(model -> new ModelPreviewDTO(
                        model.getModelId(),
                        model.getModelName(),
                        model.getDisplayName(),
                        buildPreviewUrl(model.getModelType(), model.getModelPreviewUrl())
                ))
                .toList();
    }
}
