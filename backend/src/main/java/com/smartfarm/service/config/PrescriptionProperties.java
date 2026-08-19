package com.smartfarm.service.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 처방 비동기 job 설정 — @Validated: 키 누락(null) 시 기동 실패로 조기 검출
 * (readTimeout null이면 RestClient가 무한 대기 팩토리로 만들어져 워커가 영구 블록될 수 있다).
 *
 * @param readTimeout   ai-server 처방 응답 대기(웜 ~16s이지만 콜드 스타트 LLM 웜업 고려 넉넉히 — handoff: 120s)
 * @param retryBackoffs 429(혼잡) 재시도 백오프 간격 목록 — 재시도 횟수 = 목록 크기(handoff: 5s·15s, 2회)
 */
@Validated
@ConfigurationProperties(prefix = "prescription")
public record PrescriptionProperties(
        @NotNull Duration readTimeout,
        @NotNull List<Duration> retryBackoffs
) {
}
