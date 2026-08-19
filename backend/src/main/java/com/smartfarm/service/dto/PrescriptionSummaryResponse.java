package com.smartfarm.service.dto;

import com.smartfarm.service.entity.PrescriptionStatus;
import java.time.LocalDateTime;

/**
 * 목록용 경량 응답 — 무거운 필드(result 본문)는 제외(contract §4 Summary 필드 확정).
 * JPQL 생성자 표현식({@code PrescriptionRepository.findSummariesByFarmId})으로만 생성된다.
 */
public record PrescriptionSummaryResponse(
        Long id,
        PrescriptionStatus status,
        String question,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
