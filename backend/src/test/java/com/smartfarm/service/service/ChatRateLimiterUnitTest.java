package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ChatRateLimiter} 단위 테스트(handoff #54 + 보안 리뷰 P1-B·P2, 2026-08-23) — 농장 단위 +
 * 사용자 단위 분당 상한·분 경계 리셋·롤백(사용자 상한 초과로 거부될 때 농장 카운트 미소비)·만료
 * 엔트리 청소를 {@link MutableClock}으로 실시간 대기 없이 검증한다(EnvThresholdAlertServiceUnitTest
 * 선례와 동일 원칙).
 */
class ChatRateLimiterUnitTest {

    private static final long FARM_ID = 1L;
    private static final long USER_ID = 10L;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-23T00:00:00Z"));
    private final ChatRateLimiter limiter = new ChatRateLimiter(clock);

    @Test
    @DisplayName("상한(10건) 이내는 계속 허용된다")
    void allowsUpToLimit() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            assertThat(limiter.tryAcquire(FARM_ID, USER_ID)).isTrue();
        }
    }

    @Test
    @DisplayName("농장 단위 상한(10건) 초과는 거부된다(같은 농장, 서로 다른 사용자)")
    void rejectsOverFarmLimit() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            assertThat(limiter.tryAcquire(FARM_ID, 100L + i)).isTrue();
        }
        assertThat(limiter.tryAcquire(FARM_ID, 999L)).isFalse();
    }

    @Test
    @DisplayName("다른 농장은 독립적으로 카운트된다")
    void countsPerFarmIndependently() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            limiter.tryAcquire(FARM_ID, 100L + i);
        }
        assertThat(limiter.tryAcquire(FARM_ID, 999L)).isFalse();
        assertThat(limiter.tryAcquire(2L, 999L)).isTrue();
    }

    @Test
    @DisplayName("사용자 단위 상한(10건) 초과 시 다른 농장으로 요청해도 거부된다(농장 단위 우회 차단)")
    void rejectsOverUserLimitAcrossDifferentFarms() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            // 매번 다른 farmId — 농장 단위 상한에는 전혀 걸리지 않는다.
            assertThat(limiter.tryAcquire(100L + i, USER_ID)).isTrue();
        }
        assertThat(limiter.tryAcquire(999L, USER_ID)).isFalse();
    }

    @Test
    @DisplayName("사용자 상한 초과로 거부된 요청은 농장 카운트를 소비하지 않는다(롤백)")
    void doesNotConsumeFarmQuotaWhenUserLimitExceeded() {
        // 사용자 상한을 다른 농장들로 채운다 — FARM_ID는 아직 한 번도 쓰지 않았다.
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            assertThat(limiter.tryAcquire(100L + i, USER_ID)).isTrue();
        }

        // 사용자 상한이 이미 소진된 상태에서 FARM_ID로 시도 → 사용자 상한에 막혀 거부.
        assertThat(limiter.tryAcquire(FARM_ID, USER_ID)).isFalse();

        // 롤백 덕분에 FARM_ID 카운트는 여전히 0 — 다른 사용자들은 이 농장에서 상한까지 전부 허용돼야 한다.
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            assertThat(limiter.tryAcquire(FARM_ID, 2000L + i)).isTrue();
        }
        assertThat(limiter.tryAcquire(FARM_ID, 3000L)).isFalse();
    }

    @Test
    @DisplayName("분 경계를 넘으면 농장·사용자 카운트가 모두 리셋되어 다시 허용된다")
    void resetsAfterMinuteBoundary() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            limiter.tryAcquire(FARM_ID, USER_ID);
        }
        assertThat(limiter.tryAcquire(FARM_ID, USER_ID)).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire(FARM_ID, USER_ID)).isTrue();
    }

    @Test
    @DisplayName("resetState() 호출 시 전 농장·사용자 카운트가 초기화된다")
    void resetStateClearsAllFarms() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            limiter.tryAcquire(FARM_ID, USER_ID);
        }
        assertThat(limiter.tryAcquire(FARM_ID, USER_ID)).isFalse();

        limiter.resetState();

        assertThat(limiter.tryAcquire(FARM_ID, USER_ID)).isTrue();
    }

    @Test
    @DisplayName("맵 크기가 청소 임계값을 넘으면 만료된(현재 분이 아닌) 엔트리가 정리된다")
    void cleansUpExpiredWindowsWhenThresholdExceeded() {
        ChatRateLimiter smallThresholdLimiter = new ChatRateLimiter(clock, 3);
        smallThresholdLimiter.tryAcquire(1L, 1L);
        smallThresholdLimiter.tryAcquire(2L, 2L);
        smallThresholdLimiter.tryAcquire(3L, 3L);
        assertThat(smallThresholdLimiter.farmWindowCount()).isEqualTo(3);
        assertThat(smallThresholdLimiter.userWindowCount()).isEqualTo(3);

        clock.advance(Duration.ofMinutes(2)); // 기존 3개 엔트리 전부 만료

        smallThresholdLimiter.tryAcquire(4L, 4L); // 임계값(3) 이상이라 청소 트리거 후 자신의 새 엔트리만 추가

        assertThat(smallThresholdLimiter.farmWindowCount()).isEqualTo(1);
        assertThat(smallThresholdLimiter.userWindowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("청소 임계값 미만이면 만료 엔트리도 그대로 남는다(불필요한 스캔 방지)")
    void doesNotCleanUpBelowThreshold() {
        ChatRateLimiter largeThresholdLimiter = new ChatRateLimiter(clock, 1000);
        largeThresholdLimiter.tryAcquire(1L, 1L);
        largeThresholdLimiter.tryAcquire(2L, 2L);

        clock.advance(Duration.ofMinutes(2));
        largeThresholdLimiter.tryAcquire(3L, 3L);

        assertThat(largeThresholdLimiter.farmWindowCount()).isEqualTo(3); // 청소되지 않고 그대로 누적
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
