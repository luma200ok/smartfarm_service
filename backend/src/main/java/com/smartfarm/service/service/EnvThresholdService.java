package com.smartfarm.service.service;

import com.smartfarm.service.dto.EnvThresholdsRequest;
import com.smartfarm.service.dto.EnvThresholdsResponse;
import com.smartfarm.service.entity.FarmEnvThreshold;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmEnvThresholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 농장별 환경 임계치 설정 CRUD(contract §4.6) — 조회는 멤버, 수정은 OWNER. 데모 계정 차단
 * 목록(contract §4.5)에 env-thresholds가 없어 demoAccountGuard를 적용하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnvThresholdService {

    private final FarmAccessGuard farmAccessGuard;
    private final FarmEnvThresholdRepository farmEnvThresholdRepository;

    public EnvThresholdsResponse findThresholds(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        return farmEnvThresholdRepository.findByFarmId(farmId)
                .map(EnvThresholdsResponse::from)
                .orElseGet(EnvThresholdsResponse::defaultDisabled);
    }

    @Transactional
    public EnvThresholdsResponse updateThresholds(Long farmId, Long userId, EnvThresholdsRequest request) {
        farmAccessGuard.requireOwner(farmId, userId);
        validateCrossRange(request);

        FarmEnvThreshold threshold = farmEnvThresholdRepository.findByFarmId(farmId)
                .orElseGet(() -> farmEnvThresholdRepository.save(FarmEnvThreshold.builder()
                        .farmId(farmId)
                        .enabled(false)
                        .build()));
        threshold.replace(request.enabled(), request.indoorTempMin(), request.indoorTempMax(),
                request.indoorHumidityMin(), request.indoorHumidityMax());
        return EnvThresholdsResponse.from(threshold);
    }

    /** min&lt;max 교차 검증(contract §4.6) — 개별 범위(-50~80·0~100)는 DTO Bean Validation이 이미 처리. */
    private void validateCrossRange(EnvThresholdsRequest request) {
        if (isInverted(request.indoorTempMin(), request.indoorTempMax())
                || isInverted(request.indoorHumidityMin(), request.indoorHumidityMax())) {
            throw new CustomException(ErrorCode.C001);
        }
    }

    private boolean isInverted(Double min, Double max) {
        return min != null && max != null && min >= max;
    }
}
