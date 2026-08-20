package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.dto.EnvironmentTodayResponse;
import com.smartfarm.service.exception.CustomException;
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
}
