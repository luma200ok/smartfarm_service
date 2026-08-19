package com.smartfarm.service.service;

import com.smartfarm.service.config.PrescriptionProperties;
import com.smartfarm.service.dto.PrescriptionResult;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.service.PrescriptionTransitionService.PrescriptionJob;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 처방 비동기 job 단일 워커 — 접수(202) 후의 실제 처리를 전담한다.
 *
 * <p><b>처리 흐름(트랜잭션 경계)</b>:
 * <ol>
 *   <li>짧은 쓰기 tx: PENDING → PROCESSING + job 스냅샷 확보(멱등 픽업 — 중복 제출은 스킵)</li>
 *   <li><b>트랜잭션 밖</b>: ai-server 호출(최대 120s) + 429 백오프 재시도 — DB 커넥션 비점유</li>
 *   <li>짧은 쓰기 tx: COMPLETED(result 저장) 또는 FAILED(P002/P003)</li>
 * </ol>
 *
 * <p><b>재시도 정책(handoff)</b>: 429(혼잡)만 백오프 재시도(기본 5s·15s 2회), 소진 시 FAILED(P003).
 * 5xx/접속불가/타임아웃은 재시도 없이 FAILED(P002) — ai-server가 죽은 상태에서의 재시도는 단일
 * 워커 큐만 막아 뒤 job들의 지연을 키운다. 백오프 대기는 워커 스레드 sleep — 단일 스레드 직렬화가
 * 설계 목적이므로 대기 중 다른 job이 앞지르지 않는 것이 오히려 의도된 동작(접수 순서 보존).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionJobWorker {

    private final ExecutorService prescriptionWorkerExecutor;
    private final PrescriptionTransitionService transitionService;
    private final AiPrescriptionClient aiPrescriptionClient;
    private final PrescriptionProperties prescriptionProperties;

    /**
     * job 제출 — 호출 전 처방 행이 반드시 커밋돼 있어야 한다(PrescriptionService.createPrescription의
     * NOT_SUPPORTED 근거 참고). 셧다운 중 거절되면 행은 PENDING으로 남고 재기동 복구가 재큐잉한다.
     */
    public void submit(Long prescriptionId) {
        try {
            prescriptionWorkerExecutor.execute(() -> process(prescriptionId));
        } catch (RejectedExecutionException e) {
            log.warn("워커 종료 중 제출 거절 — PENDING 유지, 재기동 복구 대상: id={}", prescriptionId);
        }
    }

    /** 워커 스레드에서 실행 — 어떤 경우에도 예외를 밖으로 던지지 않는다(단일 스레드 보호). */
    private void process(Long prescriptionId) {
        Optional<PrescriptionJob> jobOpt;
        try {
            jobOpt = transitionService.markProcessing(prescriptionId);
        } catch (Exception e) {
            // 픽업 자체가 실패(DB 장애 등) — 상태를 건드리지 못했으므로 PENDING으로 남고 복구 대상
            log.error("처방 픽업 실패 — PENDING 유지: id={}", prescriptionId, e);
            return;
        }
        if (jobOpt.isEmpty()) {
            return; // 중복 제출/이미 처리됨 — 멱등 스킵
        }

        try {
            PrescriptionResult result = callWithRetry(jobOpt.get());
            transitionService.markCompleted(prescriptionId, result);
        } catch (CustomException e) {
            ErrorCode code = e.getErrorCode() == ErrorCode.P003 ? ErrorCode.P003 : ErrorCode.P002;
            safelyFail(prescriptionId, code);
        } catch (Exception e) {
            log.error("처방 처리 중 예상 밖 오류: id={}", prescriptionId, e);
            safelyFail(prescriptionId, ErrorCode.P002);
        }
    }

    /** 429(P003)만 백오프 재시도 — 총 시도 = 1 + retryBackoffs 크기. 그 외 오류는 즉시 전파. */
    private PrescriptionResult callWithRetry(PrescriptionJob job) {
        List<Duration> backoffs = prescriptionProperties.retryBackoffs();
        int maxAttempts = backoffs.size() + 1;
        for (int attempt = 1; ; attempt++) {
            try {
                return aiPrescriptionClient.prescribe(job.question(), job.diagnosisJson());
            } catch (CustomException e) {
                if (e.getErrorCode() != ErrorCode.P003 || attempt >= maxAttempts) {
                    throw e;
                }
                log.info("ai-server 혼잡(429) — 백오프 후 재시도 {}/{}: id={}", attempt, maxAttempts - 1, job.id());
                sleep(backoffs.get(attempt - 1));
            }
        }
    }

    private void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            // 셧다운(shutdownNow) 인터럽트 — 재시도를 접고 P002로 수렴시킨다(best effort,
            // DB까지 이미 닫혔으면 PROCESSING으로 남아 재기동 복구가 정리)
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.P002);
        }
    }

    /** 실패 기록도 실패할 수 있다(셧다운 중 DB 종료 등) — 로그만 남기고 재기동 복구에 맡긴다. */
    private void safelyFail(Long prescriptionId, ErrorCode errorCode) {
        try {
            transitionService.markFailed(prescriptionId, errorCode);
        } catch (Exception e) {
            log.error("처방 실패 기록 불가 — PROCESSING 잔존, 재기동 복구 대상: id={}", prescriptionId, e);
        }
    }
}
