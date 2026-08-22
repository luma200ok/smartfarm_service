package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.dto.EnvironmentHistoryResponse;
import com.smartfarm.service.dto.EnvironmentTodayResponse;
import com.smartfarm.service.entity.EnvSnapshot;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.repository.EnvSnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnvironmentService {

    private final FarmAccessGuard farmAccessGuard;
    private final AiEnvironmentClient aiEnvironmentClient;
    private final EnvironmentCache environmentCache;
    private final EnvSnapshotRepository envSnapshotRepository;

    /**
     * 트랜잭션 밖에서 실행(외부 API 호출은 트랜잭션 밖 — 프로젝트 룰, DiagnosisService.createDiagnosis와
     * 동일 원칙). 가드 조회(farmAccessGuard)는 Spring Data JPA 리포지토리 자체 트랜잭션으로 원자성이
     * 보장되므로 이 메서드가 논트랜잭션이어도 문제 없다.
     *
     * <p>순서: 가드(멤버십) → 신선 캐시 히트면 즉시 반환(ai-server 호출 없음) → ai-server 프록시 →
     * 성공 시 캐시 갱신 → 실패(D003)면 만료된 캐시라도 있으면 그 값을 폴백 반환(contract handoff:
     * "ai-server 장애 시 D003 — 단 캐시가 살아 있으면 캐시 반환").
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EnvironmentTodayResponse findTodayEnvironment(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);

        Optional<EnvironmentTodayResponse> fresh = environmentCache.getIfFresh();
        if (fresh.isPresent()) {
            return fresh.get();
        }

        try {
            AiEnvironmentResponse aiResponse = aiEnvironmentClient.fetchToday();
            EnvironmentTodayResponse response = EnvironmentTodayResponse.from(aiResponse);
            environmentCache.put(response);
            return response;
        } catch (CustomException e) {
            return environmentCache.getStale().orElseThrow(() -> e);
        }
    }

    /**
     * 환경 시계열 이력 조회(contract §4.6) — 24h는 원본 그대로, 7d/30d는 DB 다운샘플 집계.
     * env_snapshots는 environment/today와 동일하게 farmId 무관 전 농장 공용 단일 스트림이라
     * farmId는 멤버십 가드(FarmAccessGuard)에만 쓰인다.
     */
    public EnvironmentHistoryResponse findHistory(Long farmId, Long userId, String rangeParam) {
        farmAccessGuard.requireMember(farmId, userId);
        EnvironmentHistoryRange range = EnvironmentHistoryRange.from(rangeParam);
        LocalDateTime since = LocalDateTime.now().minus(range.window());

        List<EnvironmentHistoryResponse.Point> points;
        if (range.bucket() == null) {
            points = envSnapshotRepository.findByCapturedAtGreaterThanEqualOrderByCapturedAtAsc(since).stream()
                    .map(this::toPoint)
                    .toList();
        } else {
            long bucketSeconds = range.bucket().toSeconds();
            points = envSnapshotRepository.findAggregated(since, bucketSeconds).stream()
                    .map(b -> new EnvironmentHistoryResponse.Point(b.getBucket(), b.getOutdoorTemp(),
                            b.getOutdoorHumidity(), b.getIndoorTemp(), b.getIndoorHumidity()))
                    .toList();
        }
        return new EnvironmentHistoryResponse(range.queryValue(), points);
    }

    private EnvironmentHistoryResponse.Point toPoint(EnvSnapshot snapshot) {
        return new EnvironmentHistoryResponse.Point(snapshot.getCapturedAt(), snapshot.getOutdoorTemp(),
                snapshot.getOutdoorHumidity(), snapshot.getIndoorTemp(), snapshot.getIndoorHumidity());
    }
}
