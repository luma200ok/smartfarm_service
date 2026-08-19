package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Diagnosis;
import com.smartfarm.service.entity.DiagnosisStatus;
import java.time.LocalDateTime;

/** 목록용 경량 응답 — 무거운 필드(camPngBase64)는 제외(contract §4 Summary 필드 확정). */
public record DiagnosisSummaryResponse(
        Long id,
        DiagnosisStatus status,
        String label,
        String labelKr,
        Double prob,
        String part,
        Long createdBy,
        LocalDateTime createdAt
) {

    public static DiagnosisSummaryResponse from(Diagnosis diagnosis) {
        return new DiagnosisSummaryResponse(
                diagnosis.getId(),
                diagnosis.getStatus(),
                diagnosis.getLabel(),
                diagnosis.getLabelKr(),
                diagnosis.getProb(),
                diagnosis.getPart(),
                diagnosis.getCreatedBy(),
                diagnosis.getCreatedAt()
        );
    }
}
