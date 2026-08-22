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
 * 농장별 환경 임계치 설정 CRUD(contract §4.6) — 조회는 멤버, 수정은 OWNER. 수정(PUT)은 데모 계정
 * 차단 목록(contract §4.5, 리뷰 P2로 목록에 추가됨)에 포함돼 demoAccountGuard를 적용한다.
 * 조회(GET)는 차단 목록에 없어 데모 계정도 허용(체험 핵심 — 다른 조회 API와 동일 원칙).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnvThresholdService {

    private final FarmAccessGuard farmAccessGuard;
    private final FarmEnvThresholdRepository farmEnvThresholdRepository;
    private final DemoAccountGuard demoAccountGuard;
    private final EnvThresholdAlertService envThresholdAlertService;

    public EnvThresholdsResponse findThresholds(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        return farmEnvThresholdRepository.findByFarmId(farmId)
                .map(EnvThresholdsResponse::from)
                .orElseGet(EnvThresholdsResponse::defaultDisabled);
    }

    @Transactional
    public EnvThresholdsResponse updateThresholds(Long farmId, Long userId, EnvThresholdsRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        validateCrossRange(request);

        FarmEnvThreshold threshold = farmEnvThresholdRepository.findByFarmId(farmId)
                .orElseGet(() -> farmEnvThresholdRepository.save(FarmEnvThreshold.builder()
                        .farmId(farmId)
                        .enabled(false)
                        .build()));
        threshold.replace(request.enabled(), request.indoorTempMin(), request.indoorTempMax(),
                request.indoorHumidityMin(), request.indoorHumidityMax());
        // 설정이 바뀐 시점부터 "새로 2틱"부터 세도록 연속 이탈 상태를 리셋한다(리뷰 P3).
        envThresholdAlertService.resetFarm(farmId);
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
