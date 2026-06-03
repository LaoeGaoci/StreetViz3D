package com.streetviz3d.backend.controller;

import com.streetviz3d.backend.dto.response.StreetPreviewResponse;
import com.streetviz3d.backend.service.StreetPredictService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/street")
@RequiredArgsConstructor
public class StreetPredictController {

    private final StreetPredictService streetPredictService;

    /**
     * 上传街道图片，调用 Mask2Former，返回完整前端渲染数据
     *
     * POST /api/street/predict
     * 参数: file (MultipartFile)
     */
    @PostMapping("/predict")
    public ResponseEntity<StreetPreviewResponse> predictStreet(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            StreetPreviewResponse response = streetPredictService.processStreetImageForFrontend(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}