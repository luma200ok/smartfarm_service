package com.smartfarm.service.init;

import com.smartfarm.service.entity.PrescriptionStatus;
import com.smartfarm.service.repository.PrescriptionRepository;
import com.smartfarm.service.service.PrescriptionJobWorker;
import com.smartfarm.service.service.PrescriptionTransitionService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 재기동 복구 — 이전 프로세스가 남긴 처방 job을 시작 시 정리한다(handoff §5).
 *
 * <ul>
 *   <li><b>PROCESSING 잔존</b> → FAILED(P002): ai-server 호출이 어디까지 진행됐는지 알 수 없어
 *       보수적으로 실패 처리(LLM 호출은 멱등이 아님 — 재실행하면 처방이 중복 생성될 수 있고,
 *       비용·시간도 재발생). 사용자는 폴링에서 FAILED를 보고 재요청한다.</li>
 *   <li><b>PENDING 잔존</b> → 재큐잉: 아직 아무 처리도 안 된 접수 건이므로 id 오름차순(접수 순서)으로
 *       워커에 다시 제출한다.</li>
 * </ul>
 *
 * <p><b>왜 ApplicationRunner가 아니라 @PostConstruct인가</b>: ApplicationRunner는 웹 서버가 요청을
 * 받기 시작한 뒤에 실행되므로, 복구 스캔 전에 신규 요청이 PROCESSING까지 진입하면 in-flight 정상
 * job을 stale로 오판해 FAILED 처리하는 레이스가 생긴다. @PostConstruct는 모든 싱글턴 초기화 단계
 * (= Tomcat이 트래픽을 받기 전, Flyway 마이그레이션 후)에 실행되므로 이 레이스가 원천 차단된다.
 * 이후 접수 제출과 재큐잉이 같은 id로 겹치는 경우는 markProcessing의 PENDING 가드(멱등 픽업)가 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionRecoveryInitializer {

    private final PrescriptionTransitionService transitionService;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionJobWorker prescriptionJobWorker;

    @PostConstruct
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
}
