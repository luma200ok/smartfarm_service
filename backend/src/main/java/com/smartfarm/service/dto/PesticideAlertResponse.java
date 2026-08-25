package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideAlertSeverity;
import com.smartfarm.service.service.PesticideReferenceProvider;
import java.time.LocalDateTime;

/** {@code GET /api/pesticide-references/alerts} 응답 1건(이슈 #128) — 유효기간 내 경보만 담긴다. */
public record PesticideAlertResponse(CropType cropType, String message, PesticideAlertSeverity severity,
                                      LocalDateTime validFrom, LocalDateTime validUntil) {

    public static PesticideAlertResponse from(PesticideReferenceProvider.PesticideAlertItem item) {
        return new PesticideAlertResponse(item.cropType(), item.message(), item.severity(),
                item.validFrom(), item.validUntil());
    }
}
