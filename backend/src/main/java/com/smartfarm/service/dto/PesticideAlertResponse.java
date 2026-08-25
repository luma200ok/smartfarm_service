package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideAlertSeverity;
import com.smartfarm.service.service.PesticideReferenceProvider;
import java.time.LocalDateTime;

/**
 * {@code GET /api/pesticide-references/alerts} 응답 1건(이슈 #128) — 유효기간 내 경보만 담긴다.
 *
 * <p>{@code source}는 {@link PesticideReferenceResponse#source()}와 동일한 계약(리뷰 P2) — 경보
 * 문구가 실제 관측 기반 공식 경보처럼 읽히기 쉬워, 참조정보와 동일하게 "내부 샘플"임을 명시한다.
 */
public record PesticideAlertResponse(CropType cropType, String message, PesticideAlertSeverity severity,
                                      LocalDateTime validFrom, LocalDateTime validUntil, String source) {

    public static PesticideAlertResponse from(PesticideReferenceProvider.PesticideAlertItem item) {
        return new PesticideAlertResponse(item.cropType(), item.message(), item.severity(),
                item.validFrom(), item.validUntil(), item.source());
    }
}
