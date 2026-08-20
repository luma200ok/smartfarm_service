package com.smartfarm.service.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 환경 대시보드 ai-server 프록시 전용 타임아웃 설정(contract §3 Phase 3 handoff — 진단용 30s
 * RestClient를 재사용하지 않고 짧은 전용 값을 둔다).
 *
 * @param readTimeout ai-server {@code GET /api/environment/today} 응답 대기(handoff: 5s — 대시보드
 *                     위젯 진입 시 동기 호출이라 진단(30s)만큼 기다리게 하면 UX가 나빠지고, 60s 서버
 *                     캐시가 있어 짧게 실패해도 다음 호출이나 잔존 캐시로 금방 회복된다)
 */
@Validated
@ConfigurationProperties(prefix = "environment-dashboard")
public record EnvironmentProperties(
        @NotNull Duration readTimeout
) {
}
