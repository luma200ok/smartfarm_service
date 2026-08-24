package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * "농장에는 항상 ADMIN이 최소 1명" 불변식의 <b>동시성 경계</b> 검증(이슈 #122).
 *
 * <p>판정이 "관리자를 세어 보고 → 역할을 바꾼다"는 check-then-act라, 잠금이 없으면 <b>두 ADMIN이
 * 서로를 동시에 강등</b>할 때 양쪽 트랜잭션이 모두 "관리자 2명"을 보고 통과해 관리자가 0명이 된다.
 * 그 농장은 구조 변경·멤버 관리·삭제가 영구 불가한 상태로 고착되고 되돌릴 사람이 남지 않는다 —
 * 되돌릴 수 없다는 점에서 상한 초과(ALR002)류보다 나쁘다.
 *
 * <p>⚠️ <b>검증 방식</b>: "N스레드를 동시에 던져 결과를 본다"는 방식은 <b>이 경로에서 회귀를 잡지
 * 못한다</b>. 세기→쓰기 구간이 워낙 짧아 스레드 기동 지터만으로 사실상 직렬 실행되기 때문에,
 * 잠금을 빼도 초록으로 통과한다(같은 이유로 {@code AlarmRuleConcurrencyTest}도 이 방식을 버렸다 —
 * 그쪽은 8스레드까지 초록이었다고 실측 기록이 남아 있다). 그래서 결과가 아니라 <b>메커니즘</b>을
 * 직접 본다: 다른 트랜잭션이 농장 행을 잡고 있는 동안 역할 변경/멤버 제거가 실제로 <b>블로킹되는가</b>.
 * 잠금을 제거하면 즉시 통과해 버리므로 이 테스트는 수정을 되돌리는 순간 빨갛게 된다.
 */
class FarmMemberRoleConcurrencyTest extends IntegrationTestSupport {

    /** 잠금이 걸려 있다면 이 시간 안에 끝나지 않아야 한다(블로킹 확인용). */
    private static final long BLOCKED_PROBE_MILLIS = 1500;

    /** 잠금 해제 후에는 이 시간 안에 끝나야 한다. */
    private static final long RELEASE_TIMEOUT_SECONDS = 20;

    @Autowired
    private AuthService authService;

    @Autowired
    private FarmService farmService;

    @Autowired
    private FarmMemberService farmMemberService;

    @Autowired
    private FarmMemberRepository farmMemberRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private record TwoAdminFarm(Long farmId, Long adminAId, Long adminBId, Long memberAId, Long memberBId) {
    }

    /** ADMIN 2명짜리 농장 — 한 명을 강등해도 아직 관리자가 남는 상태(정상 경로가 성립하는 픽스처). */
    private TwoAdminFarm createFarmWithTwoAdmins(String tag) {
        Long adminAId = authService.signup(new SignupRequest(
                tag + "-a-" + UUID.randomUUID() + "@example.com", "password123", tag + "갑")).id();
        Long adminBId = authService.signup(new SignupRequest(
                tag + "-b-" + UUID.randomUUID() + "@example.com", "password123", tag + "을")).id();
        Long farmId = farmService.createFarm(adminAId,
                new FarmRequest(tag + " 농장", CropType.TOMATO, null)).id();
        farmMemberRepository.saveAndFlush(FarmMember.builder()
                .farmId(farmId).userId(adminBId).role(FarmRole.ADMIN).build());

        Long memberAId = farmMemberRepository.findLiveByFarmIdAndUserId(farmId, adminAId)
                .orElseThrow().getId();
        Long memberBId = farmMemberRepository.findLiveByFarmIdAndUserId(farmId, adminBId)
                .orElseThrow().getId();
        return new TwoAdminFarm(farmId, adminAId, adminBId, memberAId, memberBId);
    }

    /**
     * 농장 행을 잠근 다른 트랜잭션이 있는 동안 {@code task}가 블로킹되는지 확인하고,
     * 잠금 해제 후에는 정상 완료되는지 확인한다.
     */
    private void assertBlocksOnFarmRowLock(Long farmId, Runnable task, String what) throws Exception {
        DefaultTransactionDefinition requiresNew =
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 편집자 A — 농장 행을 SELECT ... FOR UPDATE로 잠근 채 커밋하지 않고 붙잡아 둔다.
        TransactionStatus holder = transactionManager.getTransaction(requiresNew);
        assertThat(farmRepository.findByIdForUpdate(farmId)).isPresent();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        executor.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                finished.countDown();
            }
        });

        try {
            assertThat(finished.await(BLOCKED_PROBE_MILLIS, TimeUnit.MILLISECONDS))
                    .as("농장 행이 잠긴 동안에도 %s 요청이 끝났다 — 관리자 수 판정이 잠금 밖에 있다는 뜻이라, "
                            + "두 ADMIN이 서로를 동시에 강등하면 둘 다 '관리자 2명'을 보고 통과해 "
                            + "관리자가 0명인 농장이 만들어진다", what)
                    .isFalse();
        } finally {
            transactionManager.commit(holder); // 잠금 해제
        }

        assertThat(finished.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("잠금 해제 후에는 %s 요청이 진행돼야 한다", what)
                .isTrue();
        executor.shutdownNow();
        assertThat(failure.get()).isNull();
    }

    @Test
    @DisplayName("농장 행이 잠겨 있는 동안 역할 변경은 블로킹된다 — 관리자 수 판정과 전이가 그 잠금 "
            + "안쪽에서 직렬화된다는 뜻(잠금이 없으면 동시 강등이 관리자 0명을 만든다)")
    void changeMemberRoleSerializesOnFarmRowLock() throws Exception {
        TwoAdminFarm fixture = createFarmWithTwoAdmins("역할동시성");

        assertBlocksOnFarmRowLock(fixture.farmId(),
                () -> farmMemberService.changeMemberRole(fixture.farmId(), fixture.adminAId(),
                        fixture.memberBId(), FarmRole.OPERATOR),
                "역할 변경");

        // 잠금 해제 후 실제로 강등됐고, 남은 관리자는 1명이다
        assertThat(farmMemberRepository.findById(fixture.memberBId()).orElseThrow().getRole())
                .isEqualTo(FarmRole.OPERATOR);
        assertThat(farmMemberRepository
                .countLiveMembersByFarmIdAndRole(fixture.farmId(), FarmRole.ADMIN))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("농장 행이 잠겨 있는 동안 멤버 제거도 블로킹된다 — 제거 경로가 역할 변경과 같은 "
            + "잠금을 잡아야 '강등 ↔ 제거' 교차 동시성에서도 마지막 관리자가 지켜진다")
    void removeMemberSerializesOnFarmRowLock() throws Exception {
        TwoAdminFarm fixture = createFarmWithTwoAdmins("제거동시성");

        assertBlocksOnFarmRowLock(fixture.farmId(),
                () -> farmMemberService.removeMember(fixture.farmId(), fixture.adminAId(),
                        fixture.memberBId()),
                "멤버 제거");

        assertThat(farmMemberRepository.findById(fixture.memberBId())).isEmpty();
        assertThat(farmMemberRepository
                .countLiveMembersByFarmIdAndRole(fixture.farmId(), FarmRole.ADMIN))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("순차 강등: 두 번째(마지막) 관리자 강등은 F006으로 거부된다 — 잠금 안쪽 판정의 정상 경로")
    void demotingLastAdminIsRejected() {
        TwoAdminFarm fixture = createFarmWithTwoAdmins("순차강등");

        farmMemberService.changeMemberRole(fixture.farmId(), fixture.adminAId(),
                fixture.memberBId(), FarmRole.OPERATOR);

        CustomException thrown = Assertions.assertThrows(CustomException.class,
                () -> farmMemberService.changeMemberRole(fixture.farmId(), fixture.adminAId(),
                        fixture.memberAId(), FarmRole.OPERATOR));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.F006);
        assertThat(farmMemberRepository
                .countLiveMembersByFarmIdAndRole(fixture.farmId(), FarmRole.ADMIN))
                .as("거부됐으므로 관리자는 그대로 1명 남아 있어야 한다")
                .isEqualTo(1);
    }
}
