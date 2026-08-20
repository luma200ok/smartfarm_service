package com.smartfarm.service.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/farms/{farmId}/environment/today} 응답(contract §3·§4 Phase 3 확정 스키마) —
 * camelCase. ai-server 원 응답({@link AiEnvironmentResponse}, snake_case)의 필드명만 변환해
 * 그대로 전달한다(값 자체를 가공·보정하지 않음 — "가용 필드 + alerts 사유" 원칙은 ai-server 책임).
 */
public record EnvironmentTodayResponse(
        boolean demo,
        LocalDateTime updatedAt,
        Weather outdoor,
        Indoor indoor,
        List<Device> devices,
        List<String> alerts
) {

    public record Weather(Double temp, Double humidity) {
    }

    public record Indoor(Double temp, Double humidity, Boolean controlled) {
    }

    public record Device(String name, Boolean on) {
    }

    /** devices/alerts 누락(null)은 빈 리스트로 정규화 — FE가 null 체크 없이 바로 순회하도록. */
    public static EnvironmentTodayResponse from(AiEnvironmentResponse ai) {
        return new EnvironmentTodayResponse(
                Boolean.TRUE.equals(ai.demo()),
                ai.updatedAt(),
                ai.outdoor() == null ? null : new Weather(ai.outdoor().temp(), ai.outdoor().humidity()),
                ai.indoor() == null ? null
                        : new Indoor(ai.indoor().temp(), ai.indoor().humidity(), ai.indoor().controlled()),
                ai.devices() == null ? List.of()
                        : ai.devices().stream().map(d -> new Device(d.name(), d.on())).toList(),
                ai.alerts() == null ? List.of() : ai.alerts()
        );
    }
}
