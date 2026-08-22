package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.entity.EnvSnapshot;
import com.smartfarm.service.repository.EnvSnapshotRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * env_snapshots 적재(contract §4.6) — {@code EnvironmentSnapshotPoller}가 매 tick 호출하는
 * 짧은 쓰기 트랜잭션. dedup 판정과 삽입을 한 트랜잭션에 묶어 판정~삽입 사이의 race를 없앤다
 * (단일 인스턴스 전제라 동시 폴러 실행 자체가 없지만, 원자성을 명시적으로 보장해둔다).
 */
@Service
@RequiredArgsConstructor
public class EnvSnapshotIngestService {

    private final EnvSnapshotRepository envSnapshotRepository;

    /**
     * ai-server 응답의 {@code updated_at}이 null이면(captured_at 앵커가 없어) 저장하지 않는다.
     * 직전 적재 행과 updated_at이 동일하면(ai-server 상태 파일 미갱신) skip한다(contract §4.6).
     *
     * @return 새로 적재됐으면 저장된 스냅샷, skip이면 empty
     */
    @Transactional
    public Optional<EnvSnapshot> ingest(AiEnvironmentResponse response) {
        if (response.updatedAt() == null) {
            return Optional.empty();
        }
        boolean duplicate = envSnapshotRepository.findFirstByOrderByCapturedAtDesc()
                .map(last -> last.getCapturedAt().equals(response.updatedAt()))
                .orElse(false);
        if (duplicate) {
            return Optional.empty();
        }

        AiEnvironmentResponse.Weather outdoor = response.outdoor();
        AiEnvironmentResponse.Indoor indoor = response.indoor();
        EnvSnapshot saved = envSnapshotRepository.save(EnvSnapshot.builder()
                .capturedAt(response.updatedAt())
                .outdoorTemp(outdoor == null ? null : outdoor.temp())
                .outdoorHumidity(outdoor == null ? null : outdoor.humidity())
                .indoorTemp(indoor == null ? null : indoor.temp())
                .indoorHumidity(indoor == null ? null : indoor.humidity())
                .controlled(indoor == null ? null : indoor.controlled())
                .build());
        return Optional.of(saved);
    }
}
