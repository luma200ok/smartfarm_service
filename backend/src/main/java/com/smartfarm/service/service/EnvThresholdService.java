package com.smartfarm.service.service;

import com.smartfarm.service.dto.EnvThresholdsRequest;
import com.smartfarm.service.dto.EnvThresholdsResponse;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.FarmEnvThreshold;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.AlarmRuleRepository;
import com.smartfarm.service.repository.FarmEnvThresholdRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 농장별 환경 임계치 설정 CRUD(contract §4.6) — 조회는 멤버, 수정은 OWNER. 수정(PUT)은 데모 계정
 * 차단 목록(contract §4.5, 리뷰 P2로 목록에 추가됨)에 포함돼 demoAccountGuard를 적용한다.
 * 조회(GET)는 차단 목록에 없어 데모 계정도 허용(체험 핵심 — 다른 조회 API와 동일 원칙).
 *
 * <p><b>#118 이후의 역할</b>: 이 API의 요청·응답 스펙과 저장소({@code farm_env_thresholds})는
 * 그대로다(하위호환). 다만 평가 엔진이 {@code alarm_rules}만 보게 됐으므로, 저장할 때마다
 * {@link #syncDerivedRules}가 이 설정에 대응하는 <b>파생 규칙</b>(온도 하한/상한, 습도 하한/상한 —
 * 최대 4개)을 동기화한다. 그 동기화가 없으면 사용자가 임계치를 바꿔도 평가는 옛 값으로 계속 돈다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnvThresholdService {

    /** 기존 "연속 2틱"(폴러 60s fixedDelay × 2)의 초 환산 — V20 이관 SQL과 같은 값이어야 한다. */
    static final int DERIVED_DURATION_SECONDS = 120;

    private final FarmAccessGuard farmAccessGuard;
    private final FarmEnvThresholdRepository farmEnvThresholdRepository;
    private final AlarmRuleRepository alarmRuleRepository;
    private final AlarmEventService alarmEventService;
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
        syncDerivedRules(threshold);
        // 설정이 바뀐 시점부터 지속시간을 새로 세도록 인메모리 상태를 리셋한다(리뷰 P3).
        envThresholdAlertService.resetFarm(farmId);
        return EnvThresholdsResponse.from(threshold);
    }

    /**
     * §4.6 설정 → {@code alarm_rules} 파생 규칙 동기화(이슈 #118).
     *
     * <p>⚠️ <b>제자리 upsert이지 삭제-후-재생성이 아니다.</b> 규칙 id가 곧
     * {@link AlarmRule#metricKey()}(알람 이벤트 멱등성 키)라서, 재생성으로 id가 바뀌면 그 규칙으로
     * 이미 열려 있던 알람 이벤트를 평가 엔진이 영영 찾지 못해(자동 해소 불가) 유령 알람이 된다.
     * 그래서 경계값이 사라지거나 설정이 비활성화되면 행을 지우는 대신 {@code enabled=false}로
     * 내리고, <b>그 즉시 열린 이벤트를 자동 해소</b>한다 — 비활성 규칙은 더 이상 평가되지 않으므로
     * 여기서 닫아 주지 않으면 그 알람도 유령으로 남는다.
     */
    private void syncDerivedRules(FarmEnvThreshold threshold) {
        List<AlarmRule> existing = alarmRuleRepository.findByThresholdId(threshold.getId());
        for (DerivedSpec spec : derivedSpecs(threshold)) {
            boolean active = threshold.isEnabled() && spec.bound() != null;
            Optional<AlarmRule> found = existing.stream()
                    .filter(rule -> rule.getMetric().equals(spec.metric().name())
                            && rule.getComparator() == spec.comparator())
                    .findFirst();

            if (found.isEmpty()) {
                if (active) {
                    alarmRuleRepository.save(AlarmRule.builder()
                            .farmId(threshold.getFarmId())
                            .name(spec.name())
                            .enabled(true)
                            .source(AlarmRuleSource.ENV_SNAPSHOT)
                            .metric(spec.metric().name())
                            .comparator(spec.comparator())
                            .thresholdValue(spec.bound())
                            .durationSeconds(DERIVED_DURATION_SECONDS)
                            .severity(AlarmSeverity.WARNING)
                            .scopeType(AlarmScopeType.FARM)
                            .thresholdId(threshold.getId())
                            .build());
                }
                continue;
            }

            AlarmRule rule = found.get();
            boolean wasEnabled = rule.isEnabled();
            // 경계값이 null이 되면(그 방향 감시 해제) threshold_value는 옛 값을 유지한 채 비활성만
            // 시킨다 — V20의 ck_alarm_rules_comparator가 GT/LT 규칙의 threshold_value NOT NULL을
            // 요구하므로 null로 되돌릴 수 없고, 되돌릴 이유도 없다(비활성 규칙은 평가되지 않는다).
            rule.syncDerived(spec.name(), active, spec.bound() != null ? spec.bound() : rule.getThresholdValue());
            if (wasEnabled && !active) {
                alarmEventService.autoResolveIfOpen(threshold.getFarmId(), rule.metricKey());
            }
        }
    }

    /** 파생 규칙 4종 정의 — V20 이관 SQL의 VALUES 목록과 같은 구성이어야 한다. */
    private List<DerivedSpec> derivedSpecs(FarmEnvThreshold threshold) {
        return List.of(
                new DerivedSpec("실내 온도 하한", EnvMetric.INDOOR_TEMP, AlarmComparator.LT,
                        threshold.getIndoorTempMin()),
                new DerivedSpec("실내 온도 상한", EnvMetric.INDOOR_TEMP, AlarmComparator.GT,
                        threshold.getIndoorTempMax()),
                new DerivedSpec("실내 습도 하한", EnvMetric.INDOOR_HUMIDITY, AlarmComparator.LT,
                        threshold.getIndoorHumidityMin()),
                new DerivedSpec("실내 습도 상한", EnvMetric.INDOOR_HUMIDITY, AlarmComparator.GT,
                        threshold.getIndoorHumidityMax()));
    }

    private record DerivedSpec(String name, EnvMetric metric, AlarmComparator comparator, Double bound) {
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
