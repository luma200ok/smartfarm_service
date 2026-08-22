package com.smartfarm.service.scheduler;

import com.smartfarm.service.service.EnvSnapshotPurgeService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * env_snapshots 90일 보존 퍼지 스케줄러(contract §4.6, 이슈 #52) — RefreshTokenPurgeScheduler
 * 패턴 그대로: 배치 삭제 반복 + 안전 상한(MAX_BATCHES) + application-test.yml에서 자동 발화 차단.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnvSnapshotPurgeScheduler {

    static final long RETENTION_DAYS = 90;
    static final int MAX_BATCHES = 20;

    private final EnvSnapshotPurgeService envSnapshotPurgeService;

    @Value("${env-snapshot.purge-batch-size:1000}")
    private int batchSize;

    @Scheduled(initialDelayString = "${env-snapshot.purge-initial-delay:P1D}",
            fixedDelayString = "${env-snapshot.purge-fixed-delay:P1D}")
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int totalDeleted = 0;
        int batches = 0;
        int deleted;
        do {
            deleted = envSnapshotPurgeService.purgeBatch(cutoff, batchSize);
            totalDeleted += deleted;
            batches++;
        } while (deleted > 0 && batches < MAX_BATCHES);

        if (totalDeleted > 0) {
            log.info("env_snapshots 퍼지 — 보존기간 {}일 경과 {}건 삭제({}배치)",
                    RETENTION_DAYS, totalDeleted, batches);
        }
    }
}
