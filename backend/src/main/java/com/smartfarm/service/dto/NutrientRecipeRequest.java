package com.smartfarm.service.dto;

import com.smartfarm.service.entity.GrowthStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 양액 배합 계산/저장 공용 요청(contract §4.9). {@code name}은 calculate(미리보기)에서는 비워도
 * 되고 저장(POST .../nutrient-recipes)에서는 필수다 — 두 엔드포인트가 이 레코드를 공유하므로
 * "저장 시 필수"는 Bean Validation이 아니라 서비스 계층(NutrientService)에서 검증한다.
 */
public record NutrientRecipeRequest(
        @Size(min = 1, max = 50, message = "레시피 이름은 1~50자여야 합니다.")
        String name,

        @NotNull(message = "생육 단계는 필수입니다.")
        GrowthStage stage,

        @NotNull(message = "목표 농도는 필수입니다.")
        @Valid
        NutrientTargetRequest target,

        @NotNull(message = "탱크 용량은 필수입니다.")
        @DecimalMin(value = "1", message = "탱크 용량은 1L 이상이어야 합니다.")
        @DecimalMax(value = "10000", message = "탱크 용량은 10000L 이하여야 합니다.")
        Double tankVolumeL,

        @NotNull(message = "농축배율은 필수입니다.")
        @DecimalMin(value = "1", message = "농축배율은 1 이상이어야 합니다.")
        @DecimalMax(value = "500", message = "농축배율은 500 이하여야 합니다.")
        Double concentrationFactor,

        @Valid
        NutrientSourceWaterRequest sourceWater
) {

    public NutrientRecipeRequest {
        // trim 후 검증(@Size) — 공백 패딩으로 길이 제한 우회 차단(FarmLogRequest 선례).
        name = name == null ? null : name.trim();
    }
}
