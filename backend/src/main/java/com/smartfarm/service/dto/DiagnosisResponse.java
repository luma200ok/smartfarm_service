package com.smartfarm.service.dto;

import com.smartfarm.service.entity.Diagnosis;
import com.smartfarm.service.entity.DiagnosisStatus;
import java.time.LocalDateTime;

/**
 * imageUrl은 image_path 저장이 성공한 진단에 한해 GET .../image 경로로 채워진다(contract §3 Phase 3).
 * 저장 실패·저장 이전(구) 데이터는 null.
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
                toImageUrl(diagnosis),
                diagnosis.getCamPngBase64(),
                diagnosis.getCreatedBy(),
                diagnosis.getCreatedAt()
        );
    }

    private static String toImageUrl(Diagnosis diagnosis) {
        if (diagnosis.getImagePath() == null) {
            return null;
        }
        return "/api/farms/%d/diagnoses/%d/image".formatted(diagnosis.getFarmId(), diagnosis.getId());
    }
}
