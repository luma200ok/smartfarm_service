package com.smartfarm.service.scheduler;

import static com.smartfarm.service.scheduler.SensorReadingPurgeScheduler.RETENTION_DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.entity.SensorSource;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * sensor_readings 90일 보존 퍼지 스케줄러 검증(contract §4.11, 이슈 #90) —
 * EnvSnapshotPurgeSchedulerTest와 동일한 방식(measured_at을 직접 다양하게 지정, purge() 직접 호출;
 * @Scheduled 자체는 application-test.yml에서 PT1H로 키워 자동 발화 차단).
 */
class SensorReadingPurgeSchedulerTest extends FarmTestSupport {

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private SensorReadingPurgeScheduler purgeScheduler;

    private long save(long farmId, long deviceId, LocalDateTime measuredAt) {
        return sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId)
                .deviceId(deviceId)
                .metric(SensorMetric.TEMPERATURE)
                .value(22.0)
                .measuredAt(measuredAt)
                .source(SensorSource.SIMULATED)
                .build()).getId();
    }

    @Test
    @DisplayName("퍼지: 보존기간(90일)을 넘긴 측정값은 삭제된다")
    void purgeDeletesReadingsBeyondRetention() throws Exception {
        String ownerToken = signupAndLogin("owner");
        long farmId = createFarm(ownerToken, "농장1");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createDevice(ownerToken, farmId, zoneId, null, null, "센서1");

        long id = save(farmId, deviceId, LocalDateTime.now().minusDays(RETENTION_DAYS + 1));

        purgeScheduler.purge();

        assertThat(sensorReadingRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("퍼지: 보존기간 이내 측정값은 남긴다")
    void purgeKeepsReadingsWithinRetention() throws Exception {
        String ownerToken = signupAndLogin("owner2");
        long farmId = createFarm(ownerToken, "농장2");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createDevice(ownerToken, farmId, zoneId, null, null, "센서1");

        long id = save(farmId, deviceId, LocalDateTime.now().minusDays(RETENTION_DAYS - 1));

        purgeScheduler.purge();

        assertThat(sensorReadingRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("퍼지: 배치 크기(테스트 설정 2)를 초과하는 대상도 반복 호출로 모두 삭제된다")
    void purgeDrainsAllRowsAcrossMultipleBatches() throws Exception {
        String ownerToken = signupAndLogin("owner3");
        long farmId = createFarm(ownerToken, "농장3");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createDevice(ownerToken, farmId, zoneId, null, null, "센서1");

        List<Long> ids = List.of(
                save(farmId, deviceId, LocalDateTime.now().minusDays(RETENTION_DAYS + 1)),
                save(farmId, deviceId, LocalDateTime.now().minusDays(RETENTION_DAYS + 2)),
                save(farmId, deviceId, LocalDateTime.now().minusDays(RETENTION_DAYS + 3)),
                save(farmId, deviceId, LocalDateTime.now().minusDays(RETENTION_DAYS + 4)),
                save(farmId, deviceId, LocalDateTime.now().minusDays(RETENTION_DAYS + 5)));

        purgeScheduler.purge();

        ids.forEach(id -> assertThat(sensorReadingRepository.findById(id)).isEmpty());
    }
}
