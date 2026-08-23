package com.smartfarm.service.dto;

import jakarta.validation.constraints.DecimalMin;

/** 원수 분석값(선택, contract §4.9) — 있으면 Ca/Mg 목표치에서 차감 보정한다. */
public record NutrientSourceWaterRequest(
        @DecimalMin(value = "0", message = "원수 Ca는 0 이상이어야 합니다.")
        Double ca,

        @DecimalMin(value = "0", message = "원수 Mg는 0 이상이어야 합니다.")
        Double mg,

        @DecimalMin(value = "0", message = "원수 EC는 0 이상이어야 합니다.")
        Double ec
) {
}
