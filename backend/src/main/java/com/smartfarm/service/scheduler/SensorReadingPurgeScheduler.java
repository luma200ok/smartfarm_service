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
 * 구조(배치 삭제 반복 + 안전 상한(MAX_BATCHES) + application-test.yml에서 자동 발화 차단)는
 * 재사용하되, 배치/상수는 **재산정한다**(사이클 2 리뷰 P1-2). 시뮬레이터 on/off와 무관하게 항상
 * 등록된다(과거에 적재된 이력은 시뮬레이터를 꺼도 90일 뒤 정리돼야 한다 —
 * {@code smartfarm.simulator.enabled}로 조건부 등록되는 대상은 시뮬레이터 자체뿐이다).
 *
 * <p>⚠️ 처리량 산정 근거(contract §4.11 "purge 처리량 ≥ 유입량" 요구 — 계약 초판이 "패턴
 * 그대로"라고만 적어 {@code env_snapshots}(유입 1,440행/일)의 배치 상수를 그대로 복사한 것이
 * 원인이었다):
 * <pre>
 * 최악 유입 = 1틱 전역 상한(sensor-simulator.max-rows-per-tick, 기본 2,000) × 1일 틱 수(1,440)
 *           = 2,880,000행/일
 * 목표 처리량(안전계수 2배 — 하루 중 일부 구간만 실행되거나 지연되는 경우 대비)
 *           = 2,880,000 × 2 = 5,760,000행/일
 * 배치 20,000행 × MAX_BATCHES 300 = 6,000,000행/일  (목표 5,760,000행/일 이상 — 충족)
 * </pre>
 * 파티셔닝(월 단위 {@code measured_at} + {@code DROP PARTITION})은 이번 범위 밖(계약 대안으로
 * 남겨둠) — 배치 삭제로도 목표 처리량을 만족하므로 이번 사이클에선 구조 변경 없이 상수만 재산정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorReadingPurgeScheduler {

    static final long RETENTION_DAYS = 90;
    static final int MAX_BATCHES = 300;

    private final SensorReadingPurgeService sensorReadingPurgeService;

    @Value("${sensor-reading.purge-batch-size:20000}")
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
