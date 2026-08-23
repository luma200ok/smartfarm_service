package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.GrowthStage;
import com.smartfarm.service.service.NutrientPresets;

/** {@code GET /api/nutrient-presets} 응답(contract §4.9) — 작물×생육단계 1건. */
public record NutrientPresetResponse(CropType cropType, GrowthStage stage, NutrientTargetResponse target) {

    public static NutrientPresetResponse of(CropType cropType, GrowthStage stage, NutrientPresets.Target target) {
        return new NutrientPresetResponse(cropType, stage,
                new NutrientTargetResponse(target.n(), target.p(), target.k(), target.ca(), target.mg(),
                        target.s()));
    }
}
