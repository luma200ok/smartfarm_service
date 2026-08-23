package com.smartfarm.service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * KMA 단기예보(getVilageFcst) 프록시 설정(contract §4.8, 이슈 #56). 기본값 없음 — service-key·
 * grid-nx/ny는 배포 시 운영 .env로 주입(ImageProperties와 동일 패턴: {@code @Validated}로 키 누락 시
 * 기동 실패해 조기 검출). serviceKey는 시크릿 — 로그·응답에 절대 노출 금지.
 *
 * @param baseUrl        공공데이터포털 고정 엔드포인트(시크릿 아님) — 테스트는 MockWebServer로 오버라이드
 * @param serviceKey     공공데이터포털 발급 서비스키(시크릿). 디코딩(Decoding) 키 사용 권장 — 인코딩
 *                       키를 그대로 넣으면 쿼리 파라미터 인코딩과 겹쳐 이중 인코딩으로 인증이 실패할 수 있다
 * @param gridNx         기상청 격자 X좌표(전역 고정 지점 — 환경 대시보드와 동일 데모 온실 위치)
 * @param gridNy         기상청 격자 Y좌표
 */
@Validated
@ConfigurationProperties(prefix = "kma")
public record KmaProperties(
        @NotBlank String baseUrl,
        @NotBlank String serviceKey,
        @NotNull Integer gridNx,
        @NotNull Integer gridNy,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
