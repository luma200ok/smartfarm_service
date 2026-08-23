package com.smartfarm.service.scheduler;

import com.smartfarm.service.service.ControlApplyLogPurgeService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * control_apply_logs 90일 보존 purge 스케줄러(contract §4.12, 이슈 #100) — 구조는
 * {@code SensorReadingPurgeScheduler}(배치 삭제 반복 + 안전 상한 + 테스트에서 자동 발화 차단)를
 * 재사용하되 <b>상수는 이 도메인의 유입량으로 다시 산정</b>했다.
 *
 * <p>⚠️ 처리량 산정 근거(contract §4.12 상한 — "purge 상한은 유입량 기준으로 산정하고 근거를 주석에
 * 계산식으로 남긴다". 사이클 2에서 {@code EnvSnapshotPurgeScheduler} 상수를 유입 120배 차이에 그대로
 * 복사해 P1이 났다. 여기서 "패턴 그대로"를 반복하지 않기 위해 두 시나리오를 모두 계산한다):
 * <pre>
 * ── 유입원: 사용자 조작 1회당 정확히 1행(apply 1건 = 로그 1건, 비상 정지 1회 = 영향받은 존 수만큼).
 *            시뮬레이터처럼 스스로 도는 배경 유입이 없다 — 이것이 sensor_readings와의 결정적 차이다.
 *
 * ── 현실 유입: 농장 10개 × 존 10개 × 일 20회 적용 = 2,000행/일
 *              (보존 90일 누적 = 180,000행 — 단일 배치로도 정리되는 규모)
 *
 * ── 방어적 상한(자동화 클라이언트가 apply를 계속 호출하는 최악):
 *      존당 분당 1회(UI·API 왕복의 현실적 상한) × 1,440분 = 1,440행/일/존
 *      × 존 10개 × 농장 100개 = 1,440,000행/일
 *      목표 처리량(안전계수 2 — 하루 중 일부 구간만 실행되거나 지연되는 경우 대비)
 *          = 1,440,000 × 2 = 2,880,000행/일
 *      배치 5,000행 × MAX_BATCHES 600 = 3,000,000행/일  (목표 2,880,000행/일 이상 — 충족)
 * </pre>
 * 즉 현실 유입 대비 1,500배 여유이며, 방어적 상한에서도 보존 90일이 성립한다(매일 순증하지 않는다).
 * sensor_readings(2,880,000행/일 유입)와 달리 파티셔닝을 검토할 규모가 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ControlApplyLogPurgeScheduler {

    static final long RETENTION_DAYS = 90;
    static final int MAX_BATCHES = 600;

    private final ControlApplyLogPurgeService controlApplyLogPurgeService;

    @Value("${control-apply-log.purge-batch-size:5000}")
    private int batchSize;

    @Scheduled(initialDelayString = "${control-apply-log.purge-initial-delay:P1D}",
            fixedDelayString = "${control-apply-log.purge-fixed-delay:P1D}")
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int totalDeleted = 0;
        int batches = 0;
        int deleted;
        do {
            deleted = controlApplyLogPurgeService.purgeBatch(cutoff, batchSize);
            totalDeleted += deleted;
            batches++;
        } while (deleted > 0 && batches < MAX_BATCHES);

        if (totalDeleted > 0) {
            log.info("control_apply_logs 퍼지 — 보존기간 {}일 경과 {}건 삭제({}배치)",
                    RETENTION_DAYS, totalDeleted, batches);
        }
    }
}
