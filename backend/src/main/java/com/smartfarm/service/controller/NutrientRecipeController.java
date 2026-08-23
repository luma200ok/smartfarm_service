package com.smartfarm.service.controller;

import com.smartfarm.service.dto.NutrientCalculationResponse;
import com.smartfarm.service.dto.NutrientRecipeRequest;
import com.smartfarm.service.dto.NutrientRecipeResponse;
import com.smartfarm.service.dto.NutrientRecipeSummaryResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.service.NutrientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "NutrientRecipe", description = "양액 배합 계산 + 레시피 API")
@RestController
@RequestMapping("/api/farms/{farmId}/nutrient-recipes")
@RequiredArgsConstructor
public class NutrientRecipeController {

    private final NutrientService nutrientService;

    @Operation(summary = "양액 배합 계산 미리보기 (멤버, 저장 없음, 데모 계정도 허용)")
    @PostMapping("/calculate")
    public ResponseEntity<NutrientCalculationResponse> calculate(@AuthenticationPrincipal Long userId,
                                                                    @PathVariable Long farmId,
                                                                    @Valid @RequestBody NutrientRecipeRequest request) {
        return ResponseEntity.ok(nutrientService.calculate(farmId, userId, request));
    }

    @Operation(summary = "양액 배합 레시피 저장 (멤버, 데모 계정도 허용)")
    @PostMapping
    public ResponseEntity<NutrientRecipeResponse> createRecipe(@AuthenticationPrincipal Long userId,
                                                                  @PathVariable Long farmId,
                                                                  @Valid @RequestBody NutrientRecipeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nutrientService.createRecipe(farmId, userId, request));
    }

    @Operation(summary = "양액 배합 레시피 목록 조회 (멤버, 최신순)")
    @GetMapping
    public ResponseEntity<PageResponse<NutrientRecipeSummaryResponse>> findRecipes(
            @AuthenticationPrincipal Long userId, @PathVariable Long farmId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(nutrientService.findRecipes(farmId, userId, pageable));
    }

    @Operation(summary = "양액 배합 레시피 단건 조회 (멤버, 계산 결과 동봉)")
    @GetMapping("/{recipeId}")
    public ResponseEntity<NutrientRecipeResponse> findRecipe(@AuthenticationPrincipal Long userId,
                                                                @PathVariable Long farmId,
                                                                @PathVariable Long recipeId) {
        return ResponseEntity.ok(nutrientService.findRecipe(farmId, userId, recipeId));
    }

    @Operation(summary = "양액 배합 레시피 수정 (작성자 본인만)")
    @PatchMapping("/{recipeId}")
    public ResponseEntity<NutrientRecipeResponse> updateRecipe(@AuthenticationPrincipal Long userId,
                                                                  @PathVariable Long farmId,
                                                                  @PathVariable Long recipeId,
                                                                  @Valid @RequestBody NutrientRecipeRequest request) {
        return ResponseEntity.ok(nutrientService.updateRecipe(farmId, userId, recipeId, request));
    }

    @Operation(summary = "양액 배합 레시피 삭제 (작성자 본인 또는 OWNER)")
    @DeleteMapping("/{recipeId}")
    public ResponseEntity<Void> deleteRecipe(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long farmId,
                                              @PathVariable Long recipeId) {
        nutrientService.deleteRecipe(farmId, userId, recipeId);
        return ResponseEntity.noContent().build();
    }
}
