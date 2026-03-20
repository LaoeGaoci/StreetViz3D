package com.streetviz3d.backend.controller;

import com.streetviz3d.backend.dto.ModelPreviewDTO;
import com.streetviz3d.backend.service.ModelQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelQueryService modelQueryService;

    @GetMapping("/type")
    public List<ModelPreviewDTO> getModelsByType(@RequestParam String type) {
        return modelQueryService.getModelsByType(type);
    }
}