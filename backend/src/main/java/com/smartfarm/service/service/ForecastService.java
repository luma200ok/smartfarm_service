package com.smartfarm.service.service;

import com.smartfarm.service.dto.ForecastResponse;
import com.smartfarm.service.exception.CustomException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 날씨예보 조회(contract §4.8, 이슈 #56) — KMA 프록시는 farmId 무관 전역 고정 지점 단일 값이라
 * farmId는 멤버십 가드(FarmAccessGuard)에만 쓰인다(EnvironmentService.findTodayEnvironment와 동일 구조).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForecastService {

    private final FarmAccessGuard farmAccessGuard;
    private final KmaForecastClient kmaForecastClient;
    private final ForecastCache forecastCache;

    /**
     * 외부 API 호출은 트랜잭션 밖(EnvironmentService.findTodayEnvironment와 동일 원칙) — 가드 조회는
     * Spring Data JPA 리포지토리 자체 트랜잭션으로 원자성이 보장된다.
     *
     * <p>순서: 가드(멤버십) → 신선 캐시 히트면 즉시 반환(KMA 호출 없음) → KMA 프록시 → 성공 시 캐시
     * 갱신 → 실패(W001)면 만료된 캐시라도 있으면 그 값을 폴백 반환(contract: "캐시·stale 모두 없고
     * KMA 실패 → W001").
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ForecastResponse findForecast(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);

        Optional<ForecastResponse> fresh = forecastCache.getIfFresh();
        if (fresh.isPresent()) {
            return fresh.get();
        }

        try {
            ForecastResponse response = kmaForecastClient.fetchForecast();
            forecastCache.put(response);
            return response;
        } catch (CustomException e) {
            return forecastCache.getStale().orElseThrow(() -> e);
        }
    }
}
