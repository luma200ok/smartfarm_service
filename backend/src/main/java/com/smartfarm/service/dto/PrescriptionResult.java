package com.smartfarm.service.dto;

import java.util.List;

/**
 * 처방 결과 구조화 JSON(contract §4: result{summary, actions[], caution, sources[]}).
 * ai-server {@code POST /api/prescriptions} 응답 매핑, JSONB 저장 직렬화, API 응답 노출에
 * 공통으로 쓴다 — 세 곳 모두 계약과 동일한 4필드 구조라 별도 내부 DTO를 두지 않는다
 * (ai-server 응답의 계약 외 추가 필드는 공용 ObjectMapper의 FAIL_ON_UNKNOWN_PROPERTIES
 * 비활성으로 무시되고, 저장 시에는 이 레코드로 정규화된 4필드만 남는다).
 */
public record PrescriptionResult(
        String summary,
        List<String> actions,
        String caution,
        List<String> sources
) {
}
