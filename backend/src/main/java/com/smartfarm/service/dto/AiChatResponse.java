package com.smartfarm.service.dto;

import java.util.List;

/**
 * ai-server(FastAPI) {@code POST /api/chat} 응답 매핑 전용 — 내부용, 계약 DTO 아님(contract §4.7).
 * 처방(AiPrescriptionResponse)과 달리 <b>신규 영문 스키마</b>라 {@code @JsonProperty} 매핑이 필요 없다.
 */
public record AiChatResponse(
        String answer,
        List<String> sources,
        boolean fallback
) {
}
