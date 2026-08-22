package com.smartfarm.service.dto;

import com.smartfarm.service.entity.FarmEnvThreshold;
import java.time.LocalDateTime;

/**
 * {@code GET/PUT /api/farms/{farmId}/env-thresholds} 응답(contract §4.6) — 미설정 농장은
 * {@link #defaultDisabled()}로 enabled=false 기본값을 합성해 반환한다(DB에 행이 없어도 404가
 * 아니라 200 기본값).
 */
public record EnvThresholdsResponse(
        boolean enabled,
        Double indoorTempMin,
        Double indoorTempMax,
        Double indoorHumidityMin,
        Double indoorHumidityMax,
        LocalDateTime updatedAt
) {

    public static EnvThresholdsResponse from(FarmEnvThreshold threshold) {
        return new EnvThresholdsResponse(
                threshold.isEnabled(),
                threshold.getIndoorTempMin(),
                threshold.getIndoorTempMax(),
                threshold.getIndoorHumidityMin(),
                threshold.getIndoorHumidityMax(),
                threshold.getUpdatedAt()
        );
    }

    public static EnvThresholdsResponse defaultDisabled() {
        return new EnvThresholdsResponse(false, null, null, null, null, null);
    }
}
