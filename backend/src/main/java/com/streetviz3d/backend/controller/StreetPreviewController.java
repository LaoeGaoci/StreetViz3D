package com.streetviz3d.backend.controller;


import com.streetviz3d.backend.dto.response.StreetPreviewResponse;
import com.streetviz3d.backend.service.StreetPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/streets")
@RequiredArgsConstructor
@CrossOrigin
public class StreetPreviewController {

    private final StreetPreviewService streetPreviewService;

    @GetMapping("/{streetId}/preview")
    public StreetPreviewResponse getStreetPreview(@PathVariable String streetId) {
        return streetPreviewService.getStreetPreview(streetId);
    }
}