package com.smartfarm.service.scheduler;

import com.smartfarm.service.service.SensorReadingPurgeService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * sensor_readings 90일 보존 purge 스케줄러(contract §4.11, 이슈 #90) — EnvSnapshotPurgeScheduler
 * 패턴 그대로: 배치 삭제 반복 + 안전 상한(MAX_BATCHES) + application-test.yml에서 자동 발화 차단.
 * 시뮬레이터 on/off와 무관하게 항상 등록된다(과거에 적재된 이력은 시뮬레이터를 꺼도 90일 뒤 정리돼야
 * 한다 — {@code smartfarm.simulator.enabled}로 조건부 등록되는 대상은 시뮬레이터 자체뿐이다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorReadingPurgeScheduler {

    static final long RETENTION_DAYS = 90;
    static final int MAX_BATCHES = 20;

    private final SensorReadingPurgeService sensorReadingPurgeService;

    @Value("${sensor-reading.purge-batch-size:1000}")
    private int batchSize;

    @Scheduled(initialDelayString = "${sensor-reading.purge-initial-delay:P1D}",
            fixedDelayString = "${sensor-reading.purge-fixed-delay:P1D}")
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int totalDeleted = 0;
        int batches = 0;
        int deleted;
        do {
            deleted = sensorReadingPurgeService.purgeBatch(cutoff, batchSize);
            totalDeleted += deleted;
            batches++;
        } while (deleted > 0 && batches < MAX_BATCHES);

        if (totalDeleted > 0) {
            log.info("sensor_readings 퍼지 — 보존기간 {}일 경과 {}건 삭제({}배치)",
                    RETENTION_DAYS, totalDeleted, batches);
        }
    }
}
