package com.smartfarm.service.scheduler;

import static com.smartfarm.service.scheduler.EnvSnapshotPurgeScheduler.RETENTION_DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.entity.EnvSnapshot;
import com.smartfarm.service.repository.EnvSnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * env_snapshots 90일 보존 퍼지 스케줄러 검증(contract §4.6, 이슈 #52) —
 * RefreshTokenPurgeSchedulerTest와 동일한 방식(captured_at을 직접 다양하게 지정, purge() 직접
 * 호출; @Scheduled 자체는 application-test.yml에서 PT1H로 키워 자동 발화 차단). env_snapshots는
 * farmId 등 자연 격리 키가 없는 전역 테이블이라 클래스를 {@link Transactional}로 감싸 다른 테스트
 * 클래스로 데이터가 새지 않게 한다(EnvironmentHistoryApiIntegrationTest와 동일 원칙).
 */
@Transactional
class EnvSnapshotPurgeSchedulerTest extends IntegrationTestSupport {

    @Autowired
    private EnvSnapshotRepository envSnapshotRepository;

    @Autowired
    private EnvSnapshotPurgeScheduler purgeScheduler;

    private long save(LocalDateTime capturedAt) {
        return envSnapshotRepository.save(EnvSnapshot.builder()
                .capturedAt(capturedAt)
                .outdoorTemp(20.0)
                .build()).getId();
    }

    @Test
    @DisplayName("퍼지: 보존기간(90일)을 넘긴 스냅샷은 삭제된다")
    void purgeDeletesSnapshotsBeyondRetention() {
        long id = save(LocalDateTime.now().minusDays(RETENTION_DAYS + 1));

        purgeScheduler.purge();

        assertThat(envSnapshotRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("퍼지: 보존기간 이내 스냅샷은 남긴다")
    void purgeKeepsSnapshotsWithinRetention() {
        long id = save(LocalDateTime.now().minusDays(RETENTION_DAYS - 1));

        purgeScheduler.purge();

        assertThat(envSnapshotRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("퍼지: 배치 크기(테스트 설정 2)를 초과하는 대상도 반복 호출로 모두 삭제된다")
    void purgeDrainsAllRowsAcrossMultipleBatches() {
        List<Long> ids = List.of(
                save(LocalDateTime.now().minusDays(RETENTION_DAYS + 1)),
                save(LocalDateTime.now().minusDays(RETENTION_DAYS + 2)),
                save(LocalDateTime.now().minusDays(RETENTION_DAYS + 3)),
                save(LocalDateTime.now().minusDays(RETENTION_DAYS + 4)),
                save(LocalDateTime.now().minusDays(RETENTION_DAYS + 5)));

        purgeScheduler.purge();

        ids.forEach(id -> assertThat(envSnapshotRepository.findById(id)).isEmpty());
    }
}
