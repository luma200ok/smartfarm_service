package com.smartfarm.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * ai-server(FastAPI) {@code POST /api/prescriptions} 응답 매핑 전용 — 내부용, 계약 DTO 아님.
 * 실제 응답 스키마는 <b>한글 키</b>다(smartfarm_ai {@code src/llm/prescribe.py} Prescription 모델,
 * contract §3 ai-server 연동 표): 근거출처만 {@code list[str]}, 나머지는 전부 {@code str}.
 * contract §4 영문 result 스키마로의 변환은 {@link PrescriptionResult#from(AiPrescriptionResponse)}.
 */
public record AiPrescriptionResponse(
        @JsonProperty("진단요약") String diagnosisSummary,
        @JsonProperty("원인") String cause,
        @JsonProperty("즉시조치") String immediateAction,
        @JsonProperty("예방") String prevention,
        @JsonProperty("재촬영시점") String reshootGuide,
        @JsonProperty("근거출처") List<String> sources
) {
}
