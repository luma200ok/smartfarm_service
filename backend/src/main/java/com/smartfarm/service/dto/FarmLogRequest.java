package com.smartfarm.service.dto;

import com.smartfarm.service.entity.FarmLogType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record FarmLogRequest(
        @NotNull(message = "작업일자는 필수입니다.")
        LocalDate logDate,

        @NotNull(message = "작업 유형은 필수입니다.")
        FarmLogType type,

        @Size(max = 1000, message = "메모는 1000자 이하여야 합니다.")
        String memo
) {

    public FarmLogRequest {
        // trim 후 검증(@Size) — 공백 패딩으로 길이 제한 우회 차단 (FarmRequest·PrescriptionRequest 선례)
        memo = memo == null ? null : memo.trim();
    }
}
