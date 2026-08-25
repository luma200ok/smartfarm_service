package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.SensorMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 저장한 분석 생성 요청(이슈 #126) — {@code GET /readings/series}의 필터(metrics/range/scope)를
 * 이름 붙여 저장한다.
 *
 * @param metrics  §4.11 {@code SensorMetric} 최대 4개(series의 {@code MAX_SERIES_METRICS}와 동일
 *                 상한 — {@code ReadingService} 상수 재사용, 서비스가 중복은 제거해 저장한다).
 * @param range    {@code EnvironmentHistoryRange.queryValue()} — "24h"|"7d"|"30d"(형식 검증은
 *                 서비스가 C001로 수행 — series의 range 파싱과 동일 로직 재사용).
 * @param scopeId  {@code scopeType=FARM}이면 null, 그 외는 그 농장 소속 zone/rack/level id
 *                 (AlarmRuleRequest와 동일 규약 — 소속 검증은 AlarmScopeResolver 재사용, 미소속은
 *                 404 R001~R003).
 */
public record SavedAnalysisRequest(
        @NotBlank @Size(max = 50) String name,
        @NotEmpty @Size(max = 4) List<@NotNull SensorMetric> metrics,
        @NotBlank String range,
        @NotNull AlarmScopeType scopeType,
        Long scopeId
) {
}
