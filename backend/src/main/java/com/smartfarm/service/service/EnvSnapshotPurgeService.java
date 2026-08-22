package com.smartfarm.service.service;

import com.smartfarm.service.repository.EnvSnapshotRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * env_snapshots 90일 보존 퍼지 배치 삭제 전용(contract §4.6) —
 * {@code RefreshTokenPurgeService} 선례와 동일하게, 스케줄러 안에서 배치 루프를 돌 때
 * self-invocation으로 {@code @Transactional}이 프록시를 우회하는 함정을 피하려 별도 빈으로 둔다.
 */
@Service
@RequiredArgsConstructor
public class EnvSnapshotPurgeService {

    private final EnvSnapshotRepository envSnapshotRepository;

    /** 배치 1건 삭제 — 반환값이 batchSize보다 작으면(0 포함) 남은 대상이 없다는 신호. */
    @Transactional
    public int purgeBatch(LocalDateTime cutoff, int batchSize) {
        return envSnapshotRepository.deleteBeforeBatch(cutoff, batchSize);
    }
}
