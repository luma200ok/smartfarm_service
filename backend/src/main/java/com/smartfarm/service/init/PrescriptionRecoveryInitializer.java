package com.smartfarm.service.init;

import com.smartfarm.service.entity.PrescriptionStatus;
import com.smartfarm.service.repository.PrescriptionRepository;
import com.smartfarm.service.service.PrescriptionJobWorker;
import com.smartfarm.service.service.PrescriptionTransitionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 재기동 복구 — 이전 프로세스가 남긴 처방 job을 시작 시 정리한다(handoff §5).
 *
 * <ul>
 *   <li><b>PROCESSING 잔존</b> → FAILED(P002): ai-server 호출이 어디까지 진행됐는지 알 수 없어
 *       보수적으로 실패 처리(LLM 호출은 멱등이 아님 — 재실행하면 처방이 중복 생성될 수 있고,
 *       비용·시간도 재발생). 사용자는 폴링에서 FAILED를 보고 재요청한다.</li>
 *   <li><b>PENDING 잔존</b> → 재큐잉: 아직 아무 처리도 안 된 접수 건이므로 id 오름차순(접수 순서)으로
 *       워커에 다시 제출한다. 연령 초과 잔존분의 P002 컷오프는 5분 주기 스위퍼
 *       ({@code PrescriptionSweeper})가 이어서 처리한다.</li>
 * </ul>
 *
 * <p><b>왜 SmartLifecycle(phase 0)인가</b>(@PostConstruct → 전환, reviewer P2):
 * <ul>
 *   <li>ApplicationRunner는 웹 서버가 요청을 받기 시작한 뒤 실행 — 복구 스캔 전에 신규 요청이
 *       PROCESSING까지 진입하면 in-flight 정상 job을 stale로 오판하는 레이스가 있다.</li>
 *   <li>@PostConstruct는 트래픽 수신 전이지만 <b>싱글턴 초기화 도중</b> 실행 — 복구가 재큐잉한 job을
 *       워커 스레드가 즉시 처리하기 시작하면, 아직 refresh 중인 컨테이너에 TransactionManager 등의
 *       lazy getBean이 워커 스레드에서 동시 진입하는 경합이 생긴다.</li>
 *   <li>SmartLifecycle.start()는 <b>모든 싱글턴 초기화 완료 후 + 웹 서버 트래픽 수신 전</b>
 *       (phase 0 &lt; Boot 웹서버 lifecycle phase) — 두 문제가 모두 없다.</li>
 * </ul>
 *
 * <p><b>단일 인스턴스 전제</b>(failAllProcessing 판단 근거) — 스케일아웃 시 owner+heartbeat 필요,
 * {@link PrescriptionTransitionService#failAllProcessing()} 참고.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionRecoveryInitializer implements SmartLifecycle {

    private final PrescriptionTransitionService transitionService;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionJobWorker prescriptionJobWorker;

    private volatile boolean running;

    /**
     * <b>기동 1회 전용 — 런타임 호출 금지.</b> 런타임의 잔존 건 수렴은 스위퍼가 담당한다.
     * 런타임에 이걸 호출하면 지금 정상 처리 중인 PROCESSING job(failAllProcessing은 in-flight를
     * 구분하지 않음)을 FAILED로 오판한다. 테스트만 "기동 직후" 상황 재현 목적으로 직접 호출한다.
     */
    public void recover() {
        int failedCount = transitionService.failAllProcessing();
        if (failedCount > 0) {
            log.warn("재기동 복구 — PROCESSING 잔존 {}건을 FAILED(P002) 처리", failedCount);
        }

        List<Long> pendingIds = prescriptionRepository.findIdsByStatus(PrescriptionStatus.PENDING);
        if (!pendingIds.isEmpty()) {
            log.info("재기동 복구 — PENDING 잔존 {}건 재큐잉", pendingIds.size());
            pendingIds.forEach(prescriptionJobWorker::submit);
        }
    }

    @Override
    public void start() {
        recover();
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** phase 0(기본) — Boot 웹서버 start-stop lifecycle(phase ≫ 0)보다 먼저 = 트래픽 수신 전 보장. */
    @Override
    public int getPhase() {
        return 0;
    }
}
