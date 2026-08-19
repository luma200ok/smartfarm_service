package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Diagnosis;
import com.smartfarm.service.entity.DiagnosisStatus;
import java.time.LocalDateTime;

/**
 * imageUrl은 항상 null — 진단 이미지 원본은 저장하지 않는다(contract §6). 필드는 계약 호환용으로만 유지.
 */
public record DiagnosisResponse(
        Long id,
        DiagnosisStatus status,
        String label,
        String labelKr,
        Double prob,
        String part,
        String reason,
        String imageUrl,
        String camPngBase64,
        Long createdBy,
        LocalDateTime createdAt
) {

    public static DiagnosisResponse from(Diagnosis diagnosis) {
        return new DiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getStatus(),
                diagnosis.getLabel(),
                diagnosis.getLabelKr(),
                diagnosis.getProb(),
                diagnosis.getPart(),
                diagnosis.getReason(),
                null,
                diagnosis.getCamPngBase64(),
                diagnosis.getCreatedBy(),
                diagnosis.getCreatedAt()
        );
    }
}
