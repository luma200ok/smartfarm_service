package com.smartfarm.service.scheduler;

import com.smartfarm.service.config.PrescriptionProperties;
import com.smartfarm.service.entity.PrescriptionStatus;
import com.smartfarm.service.repository.PrescriptionRepository;
import com.smartfarm.service.service.PrescriptionJobWorker;
import com.smartfarm.service.service.PrescriptionTransitionService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 처방 job 스위퍼 — 런타임에 흘린 건(제출 거절·safelyFail 실패 등)을 5분 주기로 수렴시킨다.
 * 재기동 복구({@code PrescriptionRecoveryInitializer})가 기동 시점 1회 정리라면, 스위퍼는
 * 런타임 지속 정리다. 모든 재큐잉은 markProcessing의 멱등 픽업 가드 덕에 중복 처리가 안 된다.
 *
 * <ol>
 *   <li><b>PENDING 연령 초과({@value #PENDING_MAX_AGE_MINUTES}분)</b> → FAILED(P002):
 *       그 나이까지 처리가 안 된 접수는 사실상 유실 — 폴링 클라이언트를 무기한 대기시키지 않는다
 *       (contract §3 "연령 컷오프 적용(초과분 P002)").</li>
 *   <li><b>PENDING 정체({@value #PENDING_REQUEUE_AFTER_MINUTES}분 경과, 컷오프 미만)</b> → 재큐잉:
 *       큐 포화 제출 거절·커밋 직후 크래시 등으로 큐에 못 들어간 건 회수.
 *       LIMIT {@value #REQUEUE_LIMIT}(Pageable) — 초과분은 다음 주기에.</li>
 *   <li><b>PROCESSING 고아</b>(처리 상한 시간 경과 + in-flight 제외) → FAILED(P002):
 *       워커가 safelyFail까지 실패하고 손을 뗀 행 정리. 상한 = readTimeout×(1+재시도) +
 *       백오프 합 + 여유({@value #PROCESSING_MARGIN_MINUTES}분) — 픽업 후 이보다 오래 걸리는
 *       정상 job은 존재할 수 없고, 지금 처리 중인 1건은 id로 제외한다(단일 워커 전제).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionSweeper {

    static final long PENDING_REQUEUE_AFTER_MINUTES = 5;
    static final long PENDING_MAX_AGE_MINUTES = 30;
    static final long PROCESSING_MARGIN_MINUTES = 5;
    static final int REQUEUE_LIMIT = 100;

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionTransitionService transitionService;
    private final PrescriptionJobWorker prescriptionJobWorker;
    private final PrescriptionProperties prescriptionProperties;

    /** initialDelay로 기동 직후 재기동 복구(SmartLifecycle)와의 중복 구동을 피한다(겹쳐도 멱등이라 무해). */
    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();

        // 순서 중요: 연령 초과분을 먼저 FAILED 처리해야 뒤 재큐잉 조회에 걸리지 않는다
        int expiredPending = transitionService.failExpiredPending(
                now.minusMinutes(PENDING_MAX_AGE_MINUTES));

        int orphanedProcessing = transitionService.failOrphanedProcessing(
                now.minus(processingCutoff()), prescriptionJobWorker.currentInFlightId());

        List<Long> requeueIds = prescriptionRepository.findIdsByStatusAndCreatedAtBefore(
                PrescriptionStatus.PENDING,
                now.minusMinutes(PENDING_REQUEUE_AFTER_MINUTES),
                PageRequest.of(0, REQUEUE_LIMIT));
        requeueIds.forEach(prescriptionJobWorker::submit);

        if (expiredPending > 0 || orphanedProcessing > 0 || !requeueIds.isEmpty()) {
            log.warn("처방 스위퍼 — PENDING 만료 {}건 FAILED, PROCESSING 고아 {}건 FAILED, PENDING {}건 재큐잉",
                    expiredPending, orphanedProcessing, requeueIds.size());
        }
    }

    /** 픽업 후 정상 job의 최대 소요 상한: readTimeout×(1+재시도 횟수) + 백오프 합 + 여유. */
    private Duration processingCutoff() {
        List<Duration> backoffs = prescriptionProperties.retryBackoffs();
        Duration cutoff = prescriptionProperties.readTimeout().multipliedBy(backoffs.size() + 1L);
        for (Duration backoff : backoffs) {
            cutoff = cutoff.plus(backoff);
        }
        return cutoff.plusMinutes(PROCESSING_MARGIN_MINUTES);
    }
}
