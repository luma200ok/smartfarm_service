package com.smartfarm.service.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 처방 비동기 job 설정.
 *
 * @param readTimeout   ai-server 처방 응답 대기(웜 ~16s이지만 콜드 스타트 LLM 웜업 고려 넉넉히 — handoff: 120s)
 * @param retryBackoffs 429(혼잡) 재시도 백오프 간격 목록 — 재시도 횟수 = 목록 크기(handoff: 5s·15s, 2회)
 */
@ConfigurationProperties(prefix = "prescription")
public record PrescriptionProperties(
        Duration readTimeout,
        List<Duration> retryBackoffs
) {
}
