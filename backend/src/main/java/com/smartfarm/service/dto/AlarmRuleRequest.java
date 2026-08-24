package com.smartfarm.service.dto;

import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 알람 규칙 생성 요청(이슈 #118).
 *
 * <p>필드 간 정합성(소스↔지표, 소스↔comparator, comparator↔threshold 조합, 소스↔스코프)은 Bean
 * Validation으로 표현할 수 없어 {@code AlarmRuleService}가 ALR003/C001로 검증한다 — DB에도 V20의
 * CHECK 제약으로 2차 방어선이 있다.
 *
 * @param metric           {@code source=ENV_SNAPSHOT}이면 {@code EnvMetric}, {@code SENSOR_READING}이면
 *                         {@code SensorMetric} 이름. {@code DEVICE_HEARTBEAT}이면 null이어야 한다.
 * @param durationSeconds  조건이 이 시간(초) 동안 지속되면 발동. 폴링 주기가 60초라 그보다 짧은 값은
 *                         사실상 "첫 관측 즉시"로 동작한다.
 * @param scopeId          {@code scopeType=FARM}이면 null, 그 외는 그 농장 소속 zone/rack/level id.
 */
public record AlarmRuleRequest(
        @NotBlank @Size(max = 50) String name,
        Boolean enabled,
        @NotNull AlarmRuleSource source,
        @Size(max = 20) String metric,
        @NotNull AlarmComparator comparator,
        Double thresholdValue,
        Double thresholdMin,
        Double thresholdMax,
        @NotNull @Min(1) @Max(86400) Integer durationSeconds,
        @NotNull AlarmSeverity severity,
        @NotNull AlarmScopeType scopeType,
        Long scopeId
) {
}
