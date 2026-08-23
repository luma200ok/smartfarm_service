package com.smartfarm.service.dto;

import com.smartfarm.service.entity.GrowthStage;
import com.smartfarm.service.entity.NutrientRecipe;
import java.time.LocalDateTime;

/** 레시피 단건 응답(contract §4.9) — calculation은 저장 시점 스냅샷(NutrientRecipe#calculationSnapshot). */
public record NutrientRecipeResponse(
        Long id,
        String name,
        GrowthStage stage,
        NutrientTargetResponse target,
        double tankVolumeL,
        double concentrationFactor,
        NutrientSourceWaterResponse sourceWater,
        NutrientCalculationResponse calculation,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NutrientRecipeResponse of(NutrientRecipe recipe, NutrientCalculationResponse calculation) {
        boolean hasSourceWater = recipe.getSourceWaterCa() != null || recipe.getSourceWaterMg() != null
                || recipe.getSourceWaterEc() != null;
        return new NutrientRecipeResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getStage(),
                new NutrientTargetResponse(recipe.getTargetN(), recipe.getTargetP(), recipe.getTargetK(),
                        recipe.getTargetCa(), recipe.getTargetMg(), recipe.getTargetS()),
                recipe.getTankVolumeL(),
                recipe.getConcentrationFactor(),
                hasSourceWater
                        ? new NutrientSourceWaterResponse(recipe.getSourceWaterCa(), recipe.getSourceWaterMg(),
                                recipe.getSourceWaterEc())
                        : null,
                calculation,
                recipe.getAuthor(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt()
        );
    }
}
