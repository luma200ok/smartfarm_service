package com.smartfarm.service.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI 챗봇 프록시 전용 타임아웃 설정(contract §4.7, 이슈 #54 — 진단·처방·환경 대시보드와 동일하게
 * base-url·연결 타임아웃은 {@link AiServerProperties}를 재사용하고 응답 타임아웃만 분리한다).
 *
 * @param readTimeout ai-server {@code POST /api/chat} 응답 대기(contract: 동기, 타임아웃 30s)
 */
@Validated
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
        @NotNull Duration readTimeout
) {
}
