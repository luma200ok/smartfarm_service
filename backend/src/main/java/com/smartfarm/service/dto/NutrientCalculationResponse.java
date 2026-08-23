package com.smartfarm.service.dto;

import java.util.List;

/** 배합 계산 결과(contract §4.9) — calculate(미리보기)·레시피 저장/조회 응답에 공통으로 실린다. */
public record NutrientCalculationResponse(
        List<NutrientTankAllocationResponse> tanks,
        double estimatedEc,
        NutrientIonBalanceResponse ionBalance,
        List<String> warnings
) {
}
