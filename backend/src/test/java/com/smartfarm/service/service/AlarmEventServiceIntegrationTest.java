package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.repository.AlarmEventRepository;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * {@link AlarmEventService} 시스템 훅(recordBreach/autoResolveIfOpen) 통합 테스트(이슈 #116) —
 * 실제 DB(partial unique index 포함)로 멱등성·자동 해소를 검증한다. EnvSnapshotIngestServiceIntegrationTest
 * 선례와 동일하게 클래스 레벨 @Transactional로 테스트 간 격리한다.
 */
@Transactional
class AlarmEventServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AlarmEventService alarmEventService;

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long createFarmId() {
        Farm farm = farmRepository.save(Farm.builder().name("테스트농장").cropType(CropType.TOMATO).build());
        return farm.getId();
    }

    @Test
    @DisplayName("동일 farm×metricKey 조합으로 연속 브리치를 기록해도 이벤트는 1건만 생성된다(멱등)")
    void recordBreachIsIdempotentForOpenEvent() {
        Long farmId = createFarmId();

        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "1차 이탈", LocalDateTime.now(), null);
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "2차 이탈(연속 틱)", LocalDateTime.now(), null);
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "3차 이탈(연속 틱)", LocalDateTime.now(), null);

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMessage()).isEqualTo("1차 이탈");
        assertThat(events.get(0).getStatus()).isEqualTo(AlarmEventStatus.UNACKNOWLEDGED);
    }

    @Test
    @DisplayName("서로 다른 metricKey는 각각 독립된 이벤트로 생성된다")
    void recordBreachCreatesSeparateEventsForDifferentMetricKeys() {
        Long farmId = createFarmId();

        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "온도 상한 초과", LocalDateTime.now(), null);
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_LOW", "온도 하한 미달", LocalDateTime.now(), null);

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("정상 복귀 감지 시 열린 이벤트가 자동으로 RESOLVED 전이되고 resolvedBy는 null이다")
    void autoResolveIfOpenResolvesOpenEvent() {
        Long farmId = createFarmId();
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "온도 상한 초과", LocalDateTime.now(), null);

        alarmEventService.autoResolveIfOpen(farmId, "INDOOR_TEMP_HIGH");

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
        assertThat(events.get(0).getResolvedBy()).isNull();
        assertThat(events.get(0).getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("자동 해소 후 같은 조합의 새 브리치는 새 이벤트로 생성된다(재발동)")
    void newBreachAfterAutoResolveCreatesNewEvent() {
        Long farmId = createFarmId();
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "1차 이탈", LocalDateTime.now(), null);
        alarmEventService.autoResolveIfOpen(farmId, "INDOOR_TEMP_HIGH");

        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "2차 이탈(재발동)", LocalDateTime.now(), null);

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).hasSize(2);
        long openCount = events.stream().filter(e -> e.getStatus() != AlarmEventStatus.RESOLVED).count();
        assertThat(openCount).isEqualTo(1);
    }

    @Test
    @DisplayName("열린 이벤트가 없는 조합에 autoResolveIfOpen을 호출해도 아무 일도 일어나지 않는다(no-op)")
    void autoResolveIfOpenIsNoOpWhenNoOpenEvent() {
        Long farmId = createFarmId();

        alarmEventService.autoResolveIfOpen(farmId, "INDOOR_TEMP_HIGH");

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                LocalDateTime.now().minusDays(1));
        assertThat(events).isEmpty();
    }

    // ── 낙관적 락 충돌(이슈 #116 P1-A, GlobalExceptionHandler 409/C005 매핑 회귀 테스트) ──────

    /**
     * 동시 acknowledge 레이스를 실제 스레드 타이밍에 맡기면 재현이 불안정해지므로, 두 편집자를
     * {@code PROPAGATION_REQUIRES_NEW} 트랜잭션으로 수동 인터리빙해 결정적으로 재현한다: 편집자B가
     * 먼저 읽고(version 0) 아무 것도 쓰지 않은 채 커밋 → 편집자A가 같은 이벤트를 읽어 acknowledge 후
     * 커밋(version 0→1 확정) → 편집자B는 여전히 메모리상 version 0인 stale 사본으로 뒤늦게 저장을
     * 시도한다. Hibernate가 DB의 실제 version(1)과 stale 사본의 version(0)이 다름을 감지해
     * {@link ObjectOptimisticLockingFailureException}을 던지는지 검증한다 — 이 예외 타입이 바로
     * {@link com.smartfarm.service.exception.GlobalExceptionHandler#handleOptimisticLockingFailure}가
     * 409/C005로 매핑하는 대상이다.
     */
    @Test
    @DisplayName("동시 acknowledge 레이스에서 뒤늦은 stale 저장은 ObjectOptimisticLockingFailureException을 던진다"
            + "(GlobalExceptionHandler가 409 C005로 매핑)")
    void concurrentAcknowledgeThrowsOptimisticLockingFailure() {
        DefaultTransactionDefinition requiresNew =
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 준비 단계도 REQUIRES_NEW로 즉시 커밋한다 — 이 테스트 클래스는 클래스 레벨 @Transactional로
        // 테스트 메서드 전체가 (테스트 종료 시 롤백되는) 트랜잭션에 묶이는데, 그 트랜잭션은 메서드가
        // 끝나기 전까지 커밋되지 않아 뒤이은 REQUIRES_NEW 트랜잭션(별도 커넥션)에서는 보이지 않는다.
        TransactionStatus setupTx = transactionManager.getTransaction(requiresNew);
        Long farmId = createFarmId();
        User userA = createUser("락검증A");
        User userB = createUser("락검증B");
        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "온도 상한 초과", LocalDateTime.now(), null);
        Long eventId = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId, LocalDateTime.now().minusDays(1))
                .get(0).getId();
        transactionManager.commit(setupTx);

        // 편집자B — version 0 상태의 stale 사본을 미리 읽어 둔다(아무 것도 쓰지 않고 즉시 커밋).
        TransactionStatus readB = transactionManager.getTransaction(requiresNew);
        AlarmEvent staleEvent = alarmEventRepository.findByIdAndFarmId(eventId, farmId).orElseThrow();
        transactionManager.commit(readB);

        // 편집자A — 같은 이벤트를 읽어 먼저 acknowledge하고 커밋(version 0 → 1 확정).
        TransactionStatus txA = transactionManager.getTransaction(requiresNew);
        AlarmEvent eventA = alarmEventRepository.findByIdAndFarmId(eventId, farmId).orElseThrow();
        eventA.acknowledge(userA);
        transactionManager.commit(txA);

        // 편집자B — A가 이미 커밋한 뒤, stale 사본(version 0)으로 뒤늦게 acknowledge를 시도한다.
        // 메모리상 상태는 여전히 UNACKNOWLEDGED로 보여 엔티티 레벨 상태 가드(AL002)는 통과한다 —
        // 충돌은 오직 버전 불일치로만 감지돼야 한다.
        staleEvent.acknowledge(userB);
        TransactionStatus txB = transactionManager.getTransaction(requiresNew);
        try {
            alarmEventRepository.saveAndFlush(staleEvent);
            transactionManager.commit(txB);
            fail("낙관적 락 충돌이 감지되지 않았습니다");
        } catch (ObjectOptimisticLockingFailureException e) {
            if (!txB.isCompleted()) {
                transactionManager.rollback(txB);
            }
        }

        AlarmEvent finalState = alarmEventRepository.findByIdAndFarmId(eventId, farmId).orElseThrow();
        assertThat(finalState.getVersion()).isEqualTo(1L);
        assertThat(finalState.getStatus()).isEqualTo(AlarmEventStatus.ACKNOWLEDGED);
        assertThat(finalState.getAcknowledgedBy()).isEqualTo(userA.getId());
    }

    private User createUser(String nickname) {
        return userRepository.save(User.builder()
                .email("lock-" + UUID.randomUUID() + "@example.com")
                .password("password123")
                .nickname(nickname)
                .isDemo(false)
                .build());
    }
}
