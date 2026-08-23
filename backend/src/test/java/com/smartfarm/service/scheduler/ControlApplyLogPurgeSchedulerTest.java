package com.smartfarm.service.scheduler;

import static com.smartfarm.service.scheduler.ControlApplyLogPurgeScheduler.RETENTION_DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.ControlApplyLog;
import com.smartfarm.service.repository.ControlApplyLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * control_apply_logs 90일 보존 퍼지 검증(contract §4.12, 이슈 #100) —
 * {@code SensorReadingPurgeSchedulerTest}와 동일 방식(applied_at을 직접 지정, purge() 직접 호출.
 * {@code @Scheduled} 자동 발화는 application-test.yml에서 PT1H로 키워 차단).
 *
 * <p>배치 크기는 테스트 프로필에서 2로 낮춰, 배치 반복(loop)이 실제로 도는지도 함께 검증한다.
 */
class ControlApplyLogPurgeSchedulerTest extends FarmTestSupport {

    @Autowired
    private ControlApplyLogRepository controlApplyLogRepository;

    @Autowired
    private ControlApplyLogPurgeScheduler purgeScheduler;

    private long save(long farmId, long zoneId, LocalDateTime appliedAt) {
        return controlApplyLogRepository.save(ControlApplyLog.builder()
                .farmId(farmId)
                .zoneId(zoneId)
                .summary("목표값 1건 적용")
                .itemCount(1)
                .appliedAt(appliedAt)
                .build()).getId();
    }

    @Test
    @DisplayName("퍼지: 보존기간(90일)을 넘긴 적용 이력은 삭제되고 이내 이력은 남는다")
    void purgeDeletesOnlyLogsBeyondRetention() throws Exception {
        String ownerToken = signupAndLogin("퍼지-주인");
        long farmId = createFarm(ownerToken, "퍼지 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");

        long expired = save(farmId, zoneId, LocalDateTime.now().minusDays(RETENTION_DAYS + 1));
        long kept = save(farmId, zoneId, LocalDateTime.now().minusDays(RETENTION_DAYS - 1));

        purgeScheduler.purge();

        assertThat(controlApplyLogRepository.findById(expired)).isEmpty();
        assertThat(controlApplyLogRepository.findById(kept)).isPresent();
    }

    @Test
    @DisplayName("퍼지: 배치 크기를 넘는 만료 이력도 배치 반복으로 전부 삭제된다")
    void purgeLoopsUntilAllExpiredLogsAreDeleted() throws Exception {
        String ownerToken = signupAndLogin("퍼지-배치");
        long farmId = createFarm(ownerToken, "퍼지배치 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");

        // 테스트 프로필 배치 크기(2)보다 많은 만료 행 — 한 번의 DELETE로는 정리되지 않는다.
        List<Long> expiredIds = List.of(
                save(farmId, zoneId, LocalDateTime.now().minusDays(RETENTION_DAYS + 1)),
                save(farmId, zoneId, LocalDateTime.now().minusDays(RETENTION_DAYS + 2)),
                save(farmId, zoneId, LocalDateTime.now().minusDays(RETENTION_DAYS + 3)),
                save(farmId, zoneId, LocalDateTime.now().minusDays(RETENTION_DAYS + 4)),
                save(farmId, zoneId, LocalDateTime.now().minusDays(RETENTION_DAYS + 5)));

        purgeScheduler.purge();

        assertThat(expiredIds).allSatisfy(id -> assertThat(controlApplyLogRepository.findById(id)).isEmpty());
    }
}
