package com.smartfarm.service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * {@code PUT /api/farms/{farmId}/env-thresholds} 요청(contract §4.6). 개별 범위 검증(온도
 * -50~80, 습도 0~100)은 Bean Validation, min&lt;max 교차 검증은 서비스에서 수행한다(핸드오프
 * 재량). 각 min/max는 nullable — 미설정이면 그 방향은 평가하지 않는다.
 */
public record EnvThresholdsRequest(
        boolean enabled,
        @DecimalMin(value = "-50") @DecimalMax(value = "80") Double indoorTempMin,
        @DecimalMin(value = "-50") @DecimalMax(value = "80") Double indoorTempMax,
        @DecimalMin(value = "0") @DecimalMax(value = "100") Double indoorHumidityMin,
        @DecimalMin(value = "0") @DecimalMax(value = "100") Double indoorHumidityMax
) {
}
