package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ChatRateLimiter} 단위 테스트(handoff #54) — 농장당 분당 상한·분 경계 리셋을
 * {@link MutableClock}으로 실시간 대기 없이 검증한다(EnvThresholdAlertServiceUnitTest 선례와 동일 원칙).
 */
class ChatRateLimiterUnitTest {

    private static final long FARM_ID = 1L;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-23T00:00:00Z"));
    private final ChatRateLimiter limiter = new ChatRateLimiter(clock);

    @Test
    @DisplayName("상한(10건) 이내는 계속 허용된다")
    void allowsUpToLimit() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            assertThat(limiter.tryAcquire(FARM_ID)).isTrue();
        }
    }

    @Test
    @DisplayName("상한(10건) 초과는 거부된다")
    void rejectsOverLimit() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            limiter.tryAcquire(FARM_ID);
        }
        assertThat(limiter.tryAcquire(FARM_ID)).isFalse();
    }

    @Test
    @DisplayName("다른 농장은 독립적으로 카운트된다")
    void countsPerFarmIndependently() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            limiter.tryAcquire(FARM_ID);
        }
        assertThat(limiter.tryAcquire(FARM_ID)).isFalse();
        assertThat(limiter.tryAcquire(2L)).isTrue();
    }

    @Test
    @DisplayName("분 경계를 넘으면 카운트가 리셋되어 다시 허용된다")
    void resetsAfterMinuteBoundary() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            limiter.tryAcquire(FARM_ID);
        }
        assertThat(limiter.tryAcquire(FARM_ID)).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire(FARM_ID)).isTrue();
    }

    @Test
    @DisplayName("resetState() 호출 시 전 농장 카운트가 초기화된다")
    void resetStateClearsAllFarms() {
        for (int i = 0; i < ChatRateLimiter.LIMIT_PER_MINUTE; i++) {
            limiter.tryAcquire(FARM_ID);
        }
        assertThat(limiter.tryAcquire(FARM_ID)).isFalse();

        limiter.resetState();

        assertThat(limiter.tryAcquire(FARM_ID)).isTrue();
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
