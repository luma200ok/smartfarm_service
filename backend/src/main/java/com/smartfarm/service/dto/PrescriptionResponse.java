package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Prescription;
import com.smartfarm.service.entity.PrescriptionStatus;
import java.time.LocalDateTime;

/**
 * 처방 상세/접수 응답(contract §4) — 폴링 클라이언트는 status가 COMPLETED/FAILED가 될 때까지
 * 2~3초 간격으로 재조회한다. result는 COMPLETED, errorCode는 FAILED에서만 채워진다.
 */
public record PrescriptionResponse(
        Long id,
        PrescriptionStatus status,
        String question,
        Long diagnosisId,
        PrescriptionResult result,
        String errorCode,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    /** result는 엔티티의 JSONB 원문이 아니라 서비스에서 파싱한 구조화 객체를 받는다(직렬화 이중화 방지). */
    public static PrescriptionResponse of(Prescription prescription, PrescriptionResult result) {
        return new PrescriptionResponse(
                prescription.getId(),
                prescription.getStatus(),
                prescription.getQuestion(),
                prescription.getDiagnosisId(),
                result,
                prescription.getErrorCode(),
                prescription.getCreatedBy(),
                prescription.getCreatedAt(),
                prescription.getCompletedAt()
        );
    }
}
