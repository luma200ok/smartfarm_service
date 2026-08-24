package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.AlarmRuleRequest;
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.AlarmRuleRepository;
import com.smartfarm.service.repository.FarmRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * 알람 규칙 생성 상한의 동시성 경계 검증(#118 보안 리뷰 P2-2).
 *
 * <p>상한 판정은 "세어 보고 → 저장"이라 잠금이 없으면 병렬 요청이 전부 검사를 통과한다. 이 상한은
 * UX 제한이 아니라 <b>자원 방어선</b>이다 — 규칙 1개가 매 폴링 틱마다 조회 1~2개를 유발하고
 * 스케줄러는 전 농장의 규칙을 한 루프로 돌기 때문에, 한 농장의 초과가 다른 농장의 알람 지연으로
 * 번진다.
 *
 * <p>⚠️ <b>검증 방식</b>: 스레드 여럿을 동시에 던지는 방식은 이 경로에서 <b>재현되지 않는다</b>
 * (실측 — 잠금을 빼도 8스레드까지 초록이었다). {@code createRule}의 "세기 → 저장" 구간이 워낙 짧아
 * 스레드 기동 지터만으로 사실상 직렬 실행되기 때문이다. 그런 테스트는 수정 전후로 모두 통과해
 * <b>회귀를 잡지 못한다</b>. 그래서 결과(성공 1건)가 아니라 <b>메커니즘</b>을 직접 검증한다:
 * 다른 트랜잭션이 농장 행을 잠근 동안 {@code createRule}이 실제로 <b>블로킹되는가</b>.
 * 잠금이 없으면 즉시 통과하므로 이 테스트는 수정을 되돌리는 순간 빨갛게 된다.
 * ({@code AlarmEventServiceIntegrationTest}가 낙관적 락 충돌을 수동 인터리빙으로 결정적으로
 * 재현한 것과 같은 원칙 — 타이밍 운에 맡기지 않는다.)
 */
class AlarmRuleConcurrencyTest extends IntegrationTestSupport {

    /** 잠금이 걸려 있다면 이 시간 안에 끝나지 않아야 한다(블로킹 확인용). */
    private static final long BLOCKED_PROBE_MILLIS = 1500;

    /** 잠금 해제 후에는 이 시간 안에 끝나야 한다. */
    private static final long RELEASE_TIMEOUT_SECONDS = 20;

    @Autowired
    private AuthService authService;

    @Autowired
    private FarmService farmService;

    @Autowired
    private AlarmRuleService alarmRuleService;

    @Autowired
    private AlarmRuleRepository alarmRuleRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private AlarmRuleRequest request(String name) {
        return new AlarmRuleRequest(name, true, AlarmRuleSource.SENSOR_READING, SensorMetric.EC.name(),
                AlarmComparator.GT, 2.8, null, null, 300, AlarmSeverity.WARNING, AlarmScopeType.FARM, null);
    }

    @Test
    @DisplayName("농장 행이 잠겨 있는 동안 규칙 생성은 블로킹된다 — 상한 판정(세기)과 저장이 그 잠금 "
            + "안쪽에서 직렬화된다는 뜻(잠금이 없으면 즉시 통과해 병렬 요청이 상한을 넘긴다)")
    void createRuleSerializesOnFarmRowLock() throws Exception {
        DefaultTransactionDefinition requiresNew =
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long ownerId = authService.signup(new SignupRequest(
                "alarm-conc-" + UUID.randomUUID() + "@example.com", "password123", "상한동시성")).id();
        Long farmId = farmService.createFarm(ownerId,
                new FarmRequest("상한 동시성 농장", CropType.TOMATO, null)).id();

        // 편집자 A — 농장 행을 SELECT ... FOR UPDATE로 잠근 채 커밋하지 않고 붙잡아 둔다.
        TransactionStatus holder = transactionManager.getTransaction(requiresNew);
        assertThat(farmRepository.findByIdForUpdate(farmId)).isPresent();

        // 편집자 B — 같은 농장에 규칙을 만들려 한다. 잠금이 걸려 있다면 여기서 멈춰야 한다.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        executor.submit(() -> {
            try {
                alarmRuleService.createRule(farmId, ownerId, request("잠금 대기 규칙"));
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                finished.countDown();
            }
        });

        try {
            assertThat(finished.await(BLOCKED_PROBE_MILLIS, TimeUnit.MILLISECONDS))
                    .as("농장 행이 잠긴 동안에도 규칙 생성이 끝났다 — 상한 판정이 잠금 밖에 있다는 뜻이라, "
                            + "병렬 요청이 모두 '세기'를 통과해 상한(%d건)을 넘길 수 있다",
                            AlarmRuleService.MAX_RULES_PER_FARM)
                    .isFalse();
        } finally {
            transactionManager.commit(holder); // 잠금 해제
        }

        assertThat(finished.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("잠금 해제 후에는 규칙 생성이 진행돼야 한다")
                .isTrue();
        executor.shutdownNow();

        assertThat(failure.get()).isNull();
        assertThat(alarmRuleRepository.countByFarmId(farmId)).isEqualTo(1);
    }

    @Test
    @DisplayName("상한(50건)을 채운 뒤 추가 생성은 ALR002로 거부된다(잠금 안쪽 판정의 정상 경로)")
    void createRuleRejectsBeyondLimit() {
        Long ownerId = authService.signup(new SignupRequest(
                "alarm-limit-" + UUID.randomUUID() + "@example.com", "password123", "상한정상")).id();
        Long farmId = farmService.createFarm(ownerId,
                new FarmRequest("상한 정상 농장", CropType.TOMATO, null)).id();
        for (int i = 0; i < AlarmRuleService.MAX_RULES_PER_FARM; i++) {
            alarmRuleService.createRule(farmId, ownerId, request("규칙" + i));
        }

        CustomException thrown = org.junit.jupiter.api.Assertions.assertThrows(CustomException.class,
                () -> alarmRuleService.createRule(farmId, ownerId, request("초과 규칙")));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.ALR002);
        assertThat(alarmRuleRepository.countByFarmId(farmId)).isEqualTo(AlarmRuleService.MAX_RULES_PER_FARM);
    }
}
