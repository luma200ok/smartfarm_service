package com.smartfarm.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * ai-server(FastAPI) {@code POST /api/diagnoses} 응답 매핑 전용 — 내부용, 계약 DTO 아님.
 * ai-server 응답에는 이 레코드에 없는 필드(probs·plant_score·part_prob·yolo)도 섞여 있지만,
 * 앱 공용 ObjectMapper는 FAIL_ON_UNKNOWN_PROPERTIES가 꺼져 있어 무시된다(AiServerClientConfig 참고).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiDiagnosisResponse(
        boolean oodBlocked,
        String reason,
        String label,
        String labelKr,
        Double prob,
        String part,
        String camPngBase64
) {
}
