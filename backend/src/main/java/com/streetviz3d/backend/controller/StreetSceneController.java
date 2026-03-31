package com.streetviz3d.backend.controller;

import com.streetviz3d.backend.dto.response.StreetSceneResponse;
import com.streetviz3d.backend.service.StreetSceneLayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/streets")
@RequiredArgsConstructor
public class StreetSceneController {

    private final StreetSceneLayoutService streetSceneLayoutService;

    @GetMapping("/{streetId}/scene")
    public StreetSceneResponse getStreetScene(@PathVariable String streetId) {
        StreetSceneResponse response = new StreetSceneResponse();
        response.setScene(streetSceneLayoutService.buildStreetScene(streetId));
        return response;
    }
}
