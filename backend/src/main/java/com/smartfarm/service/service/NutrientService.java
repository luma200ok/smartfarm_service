package com.smartfarm.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.service.dto.NutrientCalculationResponse;
import com.smartfarm.service.dto.NutrientPresetResponse;
import com.smartfarm.service.dto.NutrientRecipeRequest;
import com.smartfarm.service.dto.NutrientRecipeResponse;
import com.smartfarm.service.dto.NutrientRecipeSummaryResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.NutrientRecipe;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.NutrientRecipeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 양액 배합 계산 + 레시피 CRUD(contract §4.9, 이슈 #64). 데모 계정도 계산·저장 허용(contract 명시,
 * "체험 핵심") — FarmLogService·ChatService와 동일한 콘텐츠 생성 원칙이라 {@link DemoAccountGuard}
 * 를 의도적으로 적용하지 않는다. 수정·삭제는 작성자 본인(삭제는 ADMIN도 가능)만 가능해 데모 계정
 * 간 상호 간섭도 자연히 격리된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NutrientService {

    private final NutrientRecipeRepository nutrientRecipeRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final NutrientCalculationEngine nutrientCalculationEngine;
    private final ObjectMapper objectMapper;

    /** {@code GET /api/nutrient-presets} — 인증만, 농장 스코프 아님. */
    public List<NutrientPresetResponse> findPresets(CropType cropType) {
        return NutrientPresets.byCropType(cropType).entrySet().stream()
                .map(entry -> NutrientPresetResponse.of(cropType, entry.getKey(), entry.getValue()))
                .toList();
    }

    /** {@code POST .../nutrient-recipes/calculate} — 저장 없는 미리보기. */
    public NutrientCalculationResponse calculate(Long farmId, Long userId, NutrientRecipeRequest request) {
        farmAccessGuard.requireMember(farmId, userId);
        return nutrientCalculationEngine.calculate(request.target(), request.tankVolumeL(),
                request.concentrationFactor(), request.sourceWater());
    }

    @Transactional
    public NutrientRecipeResponse createRecipe(Long farmId, Long userId, NutrientRecipeRequest request) {
        // OPERATOR 이상(이슈 #122) — VIEWER가 저장하면 author가 되어 deleteRecipe의 "author OR ADMIN"
        // 규칙으로 삭제 권한까지 갖는다. "조회만"이라는 역할 정의가 작성 경로로 우회되는 것을 막는다.
        // (미리보기 calculate는 저장이 없으므로 requireMember 그대로 — VIEWER도 계산은 볼 수 있다.)
        farmAccessGuard.requireOperator(farmId, userId);
        requireName(request);

        NutrientCalculationResponse calculation = nutrientCalculationEngine.calculate(request.target(),
                request.tankVolumeL(), request.concentrationFactor(), request.sourceWater());

        NutrientRecipe recipe = nutrientRecipeRepository.save(NutrientRecipe.builder()
                .farmId(farmId)
                .author(userId)
                .name(request.name())
                .stage(request.stage())
                .targetN(request.target().n())
                .targetP(request.target().p())
                .targetK(request.target().k())
                .targetCa(request.target().ca())
                .targetMg(request.target().mg())
                .targetS(request.target().s())
                .tankVolumeL(request.tankVolumeL())
                .concentrationFactor(request.concentrationFactor())
                .sourceWaterCa(request.sourceWater() == null ? null : request.sourceWater().ca())
                .sourceWaterMg(request.sourceWater() == null ? null : request.sourceWater().mg())
                .sourceWaterEc(request.sourceWater() == null ? null : request.sourceWater().ec())
                .calculationSnapshot(writeSnapshot(calculation))
                .build());

        return NutrientRecipeResponse.of(recipe, calculation);
    }

    public PageResponse<NutrientRecipeSummaryResponse> findRecipes(Long farmId, Long userId, Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<NutrientRecipeSummaryResponse> page = nutrientRecipeRepository
                .findByFarmIdOrderByCreatedAtDescIdDesc(farmId, pageable)
                .map(recipe -> NutrientRecipeSummaryResponse.of(recipe, readSnapshot(recipe).estimatedEc()));
        return PageResponse.of(page);
    }

    public NutrientRecipeResponse findRecipe(Long farmId, Long userId, Long recipeId) {
        farmAccessGuard.requireMember(farmId, userId);
        NutrientRecipe recipe = findRecipeOrThrow(farmId, recipeId);
        return NutrientRecipeResponse.of(recipe, readSnapshot(recipe));
    }

    @Transactional
    public NutrientRecipeResponse updateRecipe(Long farmId, Long userId, Long recipeId,
                                                NutrientRecipeRequest request) {
        farmAccessGuard.requireOperator(farmId, userId);
        requireName(request);
        NutrientRecipe recipe = findRecipeOrThrow(farmId, recipeId);
        if (!recipe.getAuthor().equals(userId)) {
            throw new CustomException(ErrorCode.N002);
        }

        NutrientCalculationResponse calculation = nutrientCalculationEngine.calculate(request.target(),
                request.tankVolumeL(), request.concentrationFactor(), request.sourceWater());

        recipe.update(request.name(), request.stage(), request.target().n(), request.target().p(),
                request.target().k(), request.target().ca(), request.target().mg(), request.target().s(),
                request.tankVolumeL(), request.concentrationFactor(),
                request.sourceWater() == null ? null : request.sourceWater().ca(),
                request.sourceWater() == null ? null : request.sourceWater().mg(),
                request.sourceWater() == null ? null : request.sourceWater().ec(),
                writeSnapshot(calculation));

        return NutrientRecipeResponse.of(recipe, calculation);
    }

    @Transactional
    public void deleteRecipe(Long farmId, Long userId, Long recipeId) {
        FarmAccessGuard.FarmAccess access = farmAccessGuard.requireOperator(farmId, userId);
        NutrientRecipe recipe = findRecipeOrThrow(farmId, recipeId);
        boolean isAuthor = recipe.getAuthor().equals(userId);
        boolean isAdmin = access.membership().getRole() == FarmRole.ADMIN;
        if (!isAuthor && !isAdmin) {
            throw new CustomException(ErrorCode.N002);
        }
        nutrientRecipeRepository.delete(recipe);
    }

    /** 저장(create/update)에서만 name 필수(contract "name?(1~50, 저장 시 필수)") — calculate는 이 검사를 타지 않는다. */
    private void requireName(NutrientRecipeRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new CustomException(ErrorCode.C001, "레시피 이름은 저장 시 필수입니다.");
        }
    }

    private NutrientRecipe findRecipeOrThrow(Long farmId, Long recipeId) {
        return nutrientRecipeRepository.findByIdAndFarmId(recipeId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.N001));
    }

    private String writeSnapshot(NutrientCalculationResponse calculation) {
        try {
            return objectMapper.writeValueAsString(calculation);
        } catch (JsonProcessingException e) {
            // 계산 결과 자체를 직렬화하지 못하면 저장이 불가능한 내부 오류 — 도메인 예외로 감싸지
            // 않고 C002(500)로 위임한다(ChatService의 외부 I/O 실패와 달리 재시도로 해결되지 않음).
            throw new IllegalStateException("양액 계산 결과 직렬화 실패", e);
        }
    }

    private NutrientCalculationResponse readSnapshot(NutrientRecipe recipe) {
        try {
            return objectMapper.readValue(recipe.getCalculationSnapshot(), NutrientCalculationResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("양액 계산 결과 역직렬화 실패(recipeId=" + recipe.getId() + ")", e);
        }
    }
}
