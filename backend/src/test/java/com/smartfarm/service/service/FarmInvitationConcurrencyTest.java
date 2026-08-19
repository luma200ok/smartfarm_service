package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.AcceptInvitationRequest;
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.FarmResponse;
import com.smartfarm.service.dto.InvitationResponse;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.entity.CropType;
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
 * 초대 수락 동시성 경계 검증 — 서비스 계층 직접 호출(스레드별 개별 트랜잭션).
 */
class FarmInvitationConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    AuthService authService;

    @Autowired
    FarmService farmService;

    @Autowired
    InvitationService invitationService;

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

    private Long signup(String nickname) {
        return authService.signup(new SignupRequest(
                "farm-conc-" + UUID.randomUUID() + "@example.com", "password123", nickname)).id();
    }

    @Test
    @DisplayName("서로 다른 두 유저가 같은 코드를 동시 수락 — 둘 다 성공, memberCount=3")
    void concurrentAcceptByTwoUsers() throws Exception {
        Long ownerId = signup("주인장");
        Long user1 = signup("동시일");
        Long user2 = signup("동시이");
        FarmResponse farm = farmService.createFarm(ownerId,
                new FarmRequest("동시 수락 농장", CropType.TOMATO, null));
        InvitationResponse invitation = invitationService.createInvitation(farm.id(), ownerId);
        AcceptInvitationRequest request = new AcceptInvitationRequest(invitation.code());

        ConcurrentResult<FarmResponse> result = runConcurrently(
                () -> invitationService.acceptInvitation(user1, request),
                () -> invitationService.acceptInvitation(user2, request));

        assertThat(result.failures()).isEmpty();
        assertThat(result.successes()).hasSize(2);
        assertThat(farmService.findFarm(farm.id(), ownerId).memberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 유저가 같은 코드를 동시 수락 — 1건 성공, 1건 F005 (unique 제약 catch 경로)")
    void concurrentAcceptBySameUser() throws Exception {
        Long ownerId = signup("주인장");
        Long joiner = signup("중복이");
        FarmResponse farm = farmService.createFarm(ownerId,
                new FarmRequest("중복 수락 농장", CropType.TOMATO, null));
        InvitationResponse invitation = invitationService.createInvitation(farm.id(), ownerId);
        AcceptInvitationRequest request = new AcceptInvitationRequest(invitation.code());

        ConcurrentResult<FarmResponse> result = runConcurrently(
                () -> invitationService.acceptInvitation(joiner, request),
                () -> invitationService.acceptInvitation(joiner, request));

        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0))
                .isInstanceOf(CustomException.class)
                .extracting(t -> ((CustomException) t).getErrorCode())
                .isEqualTo(ErrorCode.F005);
        assertThat(farmService.findFarm(farm.id(), ownerId).memberCount()).isEqualTo(2);
    }
}
