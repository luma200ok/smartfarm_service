package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.LoginRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.dto.TokenResponse;
import com.smartfarm.service.dto.UserResponse;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 동시성 경계 검증 — 서비스 계층 직접 호출(스레드별 개별 트랜잭션).
 */
class AuthConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    AuthService authService;

    /** 두 작업을 latch로 동시에 출발시키고 (성공 결과, 예외) 목록을 수집한다. */
    private <T> ConcurrentResult<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<T> successes = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (Callable<T> task : List.of(first, second)) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    successes.add(task.call());
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();
        return new ConcurrentResult<>(successes, failures);
    }

    record ConcurrentResult<T>(List<T> successes, List<Throwable> failures) {
    }

    @Test
    @DisplayName("동일 refresh 토큰 동시 요청 — 정확히 1건 성공, 1건 A004, 이후 전체 무효화")
    void concurrentRefreshWithSameToken() throws Exception {
        authService.signup(new SignupRequest("concurrent-refresh@example.com", "password123", "동시유저"));
        TokenResponse tokens = authService.login(
                new LoginRequest("concurrent-refresh@example.com", "password123"));
        String sharedRefreshToken = tokens.refreshToken();

        ConcurrentResult<TokenResponse> result = runConcurrently(
                () -> authService.refresh(sharedRefreshToken),
                () -> authService.refresh(sharedRefreshToken));

        // 정확히 1건 성공 / 1건 A004
        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0))
                .isInstanceOf(CustomException.class)
                .extracting(t -> ((CustomException) t).getErrorCode())
                .isEqualTo(ErrorCode.A004);

        // 재사용 감지로 전체 무효화 → 승자의 새 refresh 토큰도 무효(A004)
        String rotatedToken = result.successes().get(0).refreshToken();
        assertThatThrownBy(() -> authService.refresh(rotatedToken))
                .isInstanceOf(CustomException.class)
                .extracting(t -> ((CustomException) t).getErrorCode())
                .isEqualTo(ErrorCode.A004);
    }

    @Test
    @DisplayName("동일 이메일 동시 가입 — 1건 성공, 1건 A001 (unique index race의 catch 경로)")
    void concurrentSignupWithSameEmail() throws Exception {
        ConcurrentResult<UserResponse> result = runConcurrently(
                () -> authService.signup(new SignupRequest("concurrent-signup@example.com", "password123", "동시가입1")),
                () -> authService.signup(new SignupRequest("concurrent-signup@example.com", "password123", "동시가입2")));

        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0))
                .isInstanceOf(CustomException.class)
                .extracting(t -> ((CustomException) t).getErrorCode())
                .isEqualTo(ErrorCode.A001);
    }
}
