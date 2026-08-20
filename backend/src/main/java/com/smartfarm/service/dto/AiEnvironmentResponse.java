package com.smartfarm.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ai-server(FastAPI) {@code GET /api/environment/today} 응답 매핑 전용 — 내부용, 계약 DTO 아님
 * (contract §3 Phase 3, 2026-08-20 확정 스키마: {@code demo, updated_at, outdoor{temp,humidity},
 * indoor{temp,humidity,controlled}, devices[{name,on}], alerts[str]}).
 *
 * <p>ai-server는 상태 파일·KMA 조회가 불가해도 항상 200 + 가용 필드만 채우고 alerts에 사유를 남긴다
 * (부분 응답). 그 부분 응답을 그대로 수용하기 위해 전 필드를 참조형으로 두어 null을 허용한다
 * (record + Jackson은 없는 필드를 null로 채워 넣을 뿐 예외를 던지지 않는다).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiEnvironmentResponse(
        Boolean demo,
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
}
