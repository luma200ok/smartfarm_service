package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ChatConcurrencyGuard} 단위 테스트(보안 리뷰 P1-A, 2026-08-23) — 전역 상한·해제·재획득. */
class ChatConcurrencyGuardUnitTest {

    private final ChatConcurrencyGuard guard = new ChatConcurrencyGuard();

    @Test
    @DisplayName("상한(2슬롯)까지는 확보되고, 그 다음은 거부된다")
    void allowsUpToLimitThenRejects() {
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();
        assertThat(guard.availablePermits()).isZero();
    }

    @Test
    @DisplayName("release() 후에는 슬롯이 다시 확보된다")
    void releaseFreesSlotForNextAcquire() {
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();

        guard.release();

        assertThat(guard.availablePermits()).isEqualTo(1);
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("resetState() 호출 시 슬롯이 전부 회복된다(테스트 격리용)")
    void resetStateRestoresFullCapacity() {
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();

        guard.resetState();

        assertThat(guard.availablePermits()).isEqualTo(ChatConcurrencyGuard.MAX_CONCURRENT_CHATS);
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();
    }
}
