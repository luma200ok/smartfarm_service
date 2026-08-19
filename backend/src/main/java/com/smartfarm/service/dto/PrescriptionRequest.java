package com.smartfarm.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescriptionRequest(
        @NotBlank(message = "질문은 필수입니다.")
        @Size(max = 500, message = "질문은 500자 이하여야 합니다.")
        String question,

        Long diagnosisId
) {

    public PrescriptionRequest {
        // trim 후 검증(@NotBlank/@Size) — 공백 패딩으로 길이 제한 우회 차단 (FarmRequest 선례)
        question = question == null ? null : question.trim();
    }
}
