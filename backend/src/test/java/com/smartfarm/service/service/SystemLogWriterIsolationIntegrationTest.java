package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.SystemLogCategory;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.SystemLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * {@link SystemLogService#record}의 실패 격리를 실제 Postgres 트랜잭션 경계로 검증한다(이슈 #129-A,
 * #116 리뷰에서 확립된 "부가 작업은 원 작업을 깨뜨리면 안 된다" 원칙).
 *
 * <p>message 컬럼은 VARCHAR(500)이라 501자를 넘기면 <b>진짜 DB 제약 위반</b>(Postgres
 * "value too long for type character varying(500)")이 난다 — 이 테스트가 잡으려는 회귀는 실제로
 * 한 번 일어났다: {@link SystemLogService#record}에 처음 {@code @Transactional(REQUIRES_NEW)}를
 * 직접 달았을 때, 저장 실패로 Hibernate가 그 트랜잭션을 내부적으로 rollback-only 표시해
 * {@code UnexpectedRollbackException}이 메서드 밖으로 튀어나가 {@code AlarmEventServiceIntegrationTest}
 * 5건이 깨졌다({@link SystemLogWriter} 클래스 주석 참고). 지금 구조({@code record}는 트랜잭션 경계
 * 밖에서 {@link SystemLogWriter#write}를 호출)라면 이 테스트는 통과해야 한다.
 *
 * <p>농장은 {@code PROPAGATION_REQUIRES_NEW}로 즉시 커밋해 만든다(AlarmEventServiceIntegrationTest
 * 선례와 동일 원칙) — 이 클래스 레벨 {@code @Transactional}(테스트 트랜잭션, 메서드 종료 시 롤백)
 * 안에서 그냥 저장하면 {@link SystemLogWriter#write}의 REQUIRES_NEW(별도 커넥션)가 그 미커밋 농장을
 * 볼 수 없어 farm_id FK 위반으로 <b>모든</b> record() 호출이 실패해버려, "긴 메시지만 실패한다"는
 * 이 테스트의 전제 자체가 깨진다.
 */
@Transactional
class SystemLogWriterIsolationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SystemLogService systemLogService;

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long createCommittedFarmId() {
        DefaultTransactionDefinition requiresNew =
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus tx = transactionManager.getTransaction(requiresNew);
        Long farmId = farmRepository.save(Farm.builder().name("격리검증농장").cropType(CropType.TOMATO).build())
                .getId();
        transactionManager.commit(tx);
        return farmId;
    }

    @Test
    @DisplayName("record()는 실제 DB 제약 위반(VARCHAR(500) 초과)에도 예외를 던지지 않는다")
    void recordDoesNotThrowOnConstraintViolation() {
        Long farmId = createCommittedFarmId();
        String tooLong = "x".repeat(501);

        assertThatCode(() -> systemLogService.record(farmId, SystemLogCategory.CONTROL, tooLong, 1L))
                .doesNotThrowAnyException();

        // 실패한 기록은 남지 않는다(격리 실패가 아니라 진짜 실패였다는 것도 함께 확인).
        assertThat(systemLogRepository.findByFarmIdOrderByOccurredAtDescIdDesc(farmId, Pageable.unpaged())
                .getContent()).isEmpty();
    }

    @Test
    @DisplayName("record() 실패 이후에도 호출측 트랜잭션(같은 커넥션)은 계속 정상 동작하고, "
            + "같은 record()는 정상 메시지로는 계속 성공한다"
            + "(REQUIRES_NEW로 격리되지 않았다면 Postgres가 트랜잭션 전체를 abort 상태로 만들어 이후 모든 SQL이 실패한다)")
    void callerTransactionSurvivesRecordFailure() {
        Long farmId = createCommittedFarmId();
        String tooLong = "x".repeat(501);

        // 원 작업을 흉내: 실패하는 로그 기록 직후, 같은(호출측) 트랜잭션에서 계속 다른 쓰기를 수행한다.
        systemLogService.record(farmId, SystemLogCategory.CONTROL, tooLong, 1L);

        Farm anotherWrite = farmRepository.save(Farm.builder().name("이후쓰기농장").cropType(CropType.TOMATO).build());
        assertThat(anotherWrite.getId()).isNotNull();

        // 정상 메시지의 기록은 계속 성공한다(REQUIRES_NEW 자체가 고장난 게 아님을 함께 확인).
        systemLogService.record(farmId, SystemLogCategory.DEVICE, "정상 메시지", 1L);
        assertThat(systemLogRepository.findByFarmIdOrderByOccurredAtDescIdDesc(farmId, Pageable.unpaged())
                .getContent()).hasSize(1);
    }
}
