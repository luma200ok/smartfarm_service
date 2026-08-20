package com.smartfarm.service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 처방 완료/실패 디스코드 웹훅 발송 설정(contract §3 Phase 3, 이슈 #21).
 *
 * @param timeout      발송 타임아웃(연결+응답 공통, handoff: 5s) — 처방용 120s 클라이언트 재사용 금지
 * @param detailBaseUrl 상세보기 링크 base — {@code {detailBaseUrl}/farms/{farmId}/prescriptions/{id}}
 */
@Validated
@ConfigurationProperties(prefix = "webhook")
public record WebhookProperties(
        @NotNull Duration timeout,
        @NotBlank String detailBaseUrl
) {
}
