package com.smartfarm.service.dto;

import com.smartfarm.service.entity.GrowthStage;
import com.smartfarm.service.entity.NutrientRecipe;
import java.time.LocalDateTime;

/** 레시피 목록 요약 응답(contract §4.9). */
public record NutrientRecipeSummaryResponse(
        Long id,
        String name,
        GrowthStage stage,
        double estimatedEc,
        Long createdBy,
        LocalDateTime createdAt
) {

    public static NutrientRecipeSummaryResponse of(NutrientRecipe recipe, double estimatedEc) {
        return new NutrientRecipeSummaryResponse(recipe.getId(), recipe.getName(), recipe.getStage(), estimatedEc,
                recipe.getAuthor(), recipe.getCreatedAt());
    }
}
