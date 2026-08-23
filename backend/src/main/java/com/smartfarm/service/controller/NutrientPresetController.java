package com.smartfarm.service.controller;

import com.smartfarm.service.dto.NutrientPresetResponse;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.service.NutrientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "NutrientPreset", description = "양액 프리셋 API")
@RestController
@RequestMapping("/api/nutrient-presets")
@RequiredArgsConstructor
public class NutrientPresetController {

    private final NutrientService nutrientService;

    @Operation(summary = "작물×생육단계별 목표 배양액 농도 프리셋 조회 (인증만)")
    @GetMapping
    public ResponseEntity<List<NutrientPresetResponse>> findPresets(@AuthenticationPrincipal Long userId,
                                                                       @RequestParam CropType cropType) {
        return ResponseEntity.ok(nutrientService.findPresets(cropType));
    }
}
