package com.smartfarm.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlarmMemoRequest(
        @NotBlank(message = "메모 내용은 필수입니다.")
        @Size(max = 1000, message = "메모는 1000자 이하여야 합니다.")
        String note
) {

    public AlarmMemoRequest {
        // trim 후 검증(@Size) — 공백 패딩으로 길이 제한 우회 차단 (FarmLogRequest 선례)
        note = note == null ? null : note.trim();
    }
}
