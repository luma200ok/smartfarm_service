package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
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
 * 탈퇴 동시성 경계 검증 — 서비스 계층 직접 호출(스레드별 개별 트랜잭션, AuthConcurrencyTest 패턴).
 * users 행 FOR UPDATE 직렬화(contract 탈퇴 봉쇄 ②): 승자 1건 성공, 패자는 락 획득 후
 * @SQLRestriction 재평가로 A004 — 500(락 충돌·stale delete)이 나오면 안 된다.
 */
class WithdrawConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    AuthService authService;

    @Autowired
    UserService userService;

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
    @DisplayName("동시 탈퇴 2회 — 정확히 1건 성공, 패자는 A004 (500·미분류 예외 금지)")
    void concurrentWithdrawDoesNotBlowUp() throws Exception {
        String email = "concurrent-withdraw-" + UUID.randomUUID() + "@example.com";
        long userId = authService.signup(new SignupRequest(email, "password123", "동시탈퇴")).id();

        ConcurrentResult<Boolean> result = runConcurrently(
                () -> {
                    userService.withdraw(userId, "password123");
                    return true;
                },
                () -> {
                    userService.withdraw(userId, "password123");
                    return true;
                });

        // FOR UPDATE 직렬화 — 승자 정확히 1건(204 상당), 패자는 @SQLRestriction 재평가로 A004
        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).hasSize(1);
        // 패자 예외는 A004여야 한다 — 다른 예외(락 충돌 500 상당)는 실패
        assertThat(result.failures()).allSatisfy(t ->
                assertThat(t).isInstanceOf(CustomException.class)
                        .extracting(e -> ((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.A004));
    }
}
