package com.streetviz3d.backend.controller;

import com.streetviz3d.backend.dto.request.StreetmixUrlRequest;
import com.streetviz3d.backend.dto.scene.StreetSceneDTO;
import com.streetviz3d.backend.service.StreetmixService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/streetmix")
@RequiredArgsConstructor
@CrossOrigin
public class StreetmixController {

    private final StreetmixService streetmixService;

    @PostMapping("/scene")
    public StreetSceneDTO buildStreetScene(@Valid @RequestBody StreetmixUrlRequest request) {
        return streetmixService.buildStreetSceneFromUrl(request.getStreetmixUrl());
    }
}