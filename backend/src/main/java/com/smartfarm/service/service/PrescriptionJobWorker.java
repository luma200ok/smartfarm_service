package com.smartfarm.service.service;

import com.smartfarm.service.config.PrescriptionProperties;
import com.smartfarm.service.dto.AiPrescriptionResponse;
import com.smartfarm.service.dto.PrescriptionResult;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.service.PrescriptionTransitionService.PrescriptionJob;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
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

    private final ThreadPoolExecutor prescriptionWorkerExecutor;
    private final PrescriptionTransitionService transitionService;
    private final AiPrescriptionClient aiPrescriptionClient;
    private final PrescriptionProperties prescriptionProperties;
    private final PrescriptionWebhookNotifier webhookNotifier;

    /**
     * 지금 처리 중인 job id(없으면 null) — 스위퍼가 고아 PROCESSING을 정리할 때 in-flight 1건을
     * 제외하기 위한 표식(단일 워커라 최대 1건). markProcessing <b>이전</b>에 set해 "표식 없는
     * PROCESSING" 창을 없앤다.
     */
    private final AtomicReference<Long> inFlightId = new AtomicReference<>();

    public Long currentInFlightId() {
        return inFlightId.get();
    }

    /**
     * 큐 여유 확인 — 접수 시 포화면 저장 없이 P004로 거절한다(contract §3 "포화 시 저장 없이 P004").
     * 확인-제출 사이 레이스로 드물게 제출이 거절될 수 있으나, 그 경우 행은 PENDING으로 남고
     * 스위퍼(5분 주기)가 회수하므로 유실은 없다(재량 결정 — 하드 실패 대신 지연 수용).
     */
    public boolean hasCapacity() {
        return prescriptionWorkerExecutor.getQueue().remainingCapacity() > 0;
    }

    /**
     * job 제출 — 호출 전 처방 행이 반드시 커밋돼 있어야 한다(PrescriptionService.createPrescription의
     * NOT_SUPPORTED 근거 참고). 거절(셧다운/큐 포화 AbortPolicy)되면 행은 PENDING으로 남고
     * 스위퍼·재기동 복구가 재큐잉한다.
     */
    public void submit(Long prescriptionId) {
        try {
            prescriptionWorkerExecutor.execute(() -> process(prescriptionId));
        } catch (RejectedExecutionException e) {
            log.warn("워커 제출 거절(셧다운/큐 포화) — PENDING 유지, 스위퍼·재기동 복구 대상: id={}", prescriptionId);
        }
    }

    private void process(Long prescriptionId) {
        inFlightId.set(prescriptionId);
        try {
            doProcess(prescriptionId);
        } finally {
            inFlightId.set(null);
        }
    }

    /** 워커 스레드에서 실행 — Exception 계열은 전부 여기서 소화한다(전파 시에도 ThreadPoolExecutor가 스레드를 재생성). */
    private void doProcess(Long prescriptionId) {
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
            // 한글 키 원 응답 → contract §4 영문 result 매핑(빈 성공 금지·크기 캡 — PrescriptionResult.from)
            PrescriptionResult result = PrescriptionResult.from(callWithRetry(jobOpt.get()));
            if (transitionService.markCompleted(prescriptionId, result)) {
                // 전이 커밋 후에만 발송(contract §3) — 워커는 이미 트랜잭션 밖이라 별도 경계 불필요.
                webhookNotifier.notifyCompleted(jobOpt.get(), result);
            }
        } catch (InterruptedException e) {
            // 셧다운(shutdownNow) 인터럽트 — 실패 기록(best effort)이 끝난 뒤에 플래그를 복원한다.
            // 복원을 먼저 하면 JDBC 드라이버가 인터럽트 상태를 보고 즉시 실패해 기록 자체가 안 된다(reviewer P3).
            try {
                safelyFail(prescriptionId, ErrorCode.P002, jobOpt.get());
            } finally {
                Thread.currentThread().interrupt();
            }
        } catch (CustomException e) {
            ErrorCode code = e.getErrorCode() == ErrorCode.P003 ? ErrorCode.P003 : ErrorCode.P002;
            safelyFail(prescriptionId, code, jobOpt.get());
        } catch (Exception e) {
            log.error("처방 처리 중 예상 밖 오류: id={}", prescriptionId, e);
            safelyFail(prescriptionId, ErrorCode.P002, jobOpt.get());
        }
    }

    /**
     * 429(P003)만 백오프 재시도 — 총 시도 = 1 + retryBackoffs 크기. 그 외 오류는 즉시 전파.
     * 백오프 대기 중 인터럽트(셧다운)는 InterruptedException 그대로 전파 — 호출부(doProcess)가
     * 실패 기록 후 플래그를 복원한다.
     */
    private AiPrescriptionResponse callWithRetry(PrescriptionJob job) throws InterruptedException {
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
                Thread.sleep(backoffs.get(attempt - 1).toMillis());
            }
        }
    }

    /**
     * 실패 기록도 실패할 수 있다(셧다운 중 DB 종료 등) — 로그만 남기고 재기동 복구에 맡긴다.
     * 전이가 실제로 커밋됐을 때만(반환 true) 웹훅을 발송한다(contract §3 — 스킵된 전이는 미발송).
     */
    private void safelyFail(Long prescriptionId, ErrorCode errorCode, PrescriptionJob job) {
        try {
            if (transitionService.markFailed(prescriptionId, errorCode)) {
                webhookNotifier.notifyFailed(job, errorCode);
            }
        } catch (Exception e) {
            log.error("처방 실패 기록 불가 — PROCESSING 잔존, 재기동 복구 대상: id={}", prescriptionId, e);
        }
    }
}
