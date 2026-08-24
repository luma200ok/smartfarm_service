package com.smartfarm.service.service;

import com.smartfarm.service.dto.AlarmRuleRequest;
import com.smartfarm.service.dto.AlarmRuleResponse;
import com.smartfarm.service.dto.AlarmRuleUpdateRequest;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.AlarmRuleRepository;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.RackRepository;
import com.smartfarm.service.repository.ZoneRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알람 규칙 CRUD(이슈 #118) — 조회는 멤버, 쓰기는 OWNER(§4.6 env-thresholds와 일관) + 데모 계정
 * 차단(A007).
 *
 * <p>이 서비스가 지켜야 하는 두 가지 교차 관심사:
 * <ul>
 *   <li><b>테넌트 격리</b>(§4.10) — {@code scopeId}는 요청 입력값이므로 그 zone/rack/level이
 *       <b>그 농장 소속인지</b> 반드시 재확인하고, 아니면 존재를 유추당하지 않도록 404(R001~R003)로
 *       응답한다.</li>
 *   <li><b>유령 알람 차단</b> — 규칙이 비활성화·삭제되면 그 규칙은 더 이상 평가되지 않으므로,
 *       평가 엔진의 자동 해소가 영영 돌지 않는다. 그래서 여기서 직접 열린 이벤트를 닫는다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmRuleService {

    /**
     * 농장당 규칙 상한(초과 시 ALR002) — #91의 리소스 생성 상한 정책과 §4.12의 "존당 PENDING 50건"
     * 선례에 맞춘 값. 규칙 하나가 매 폴링 틱마다 조회 1~2개를 유발하므로 무제한이면 틱 비용이
     * 사용자 입력에 비례해 무한정 커진다.
     */
    static final int MAX_RULES_PER_FARM = 50;

    private final AlarmRuleRepository alarmRuleRepository;
    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;
    private final RackLevelRepository rackLevelRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final DemoAccountGuard demoAccountGuard;
    private final AlarmEventService alarmEventService;
    private final EnvThresholdAlertService envThresholdAlertService;

    public List<AlarmRuleResponse> findRules(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        return alarmRuleRepository.findByFarmIdOrderByIdAsc(farmId).stream()
                .map(AlarmRuleResponse::from)
                .toList();
    }

    public AlarmRuleResponse findRule(Long farmId, Long userId, Long ruleId) {
        farmAccessGuard.requireMember(farmId, userId);
        return AlarmRuleResponse.from(findRuleOrThrow(farmId, ruleId));
    }

    @Transactional
    public AlarmRuleResponse createRule(Long farmId, Long userId, AlarmRuleRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);

        if (alarmRuleRepository.countByFarmId(farmId) >= MAX_RULES_PER_FARM) {
            throw new CustomException(ErrorCode.ALR002,
                    "농장당 알람 규칙은 최대 " + MAX_RULES_PER_FARM + "개까지 등록할 수 있습니다.");
        }
        validateSourceAndMetric(request.source(), request.metric());
        validateComparator(request.source(), request.comparator(),
                request.thresholdValue(), request.thresholdMin(), request.thresholdMax());
        validateScope(farmId, request.source(), request.scopeType(), request.scopeId());

        AlarmRule rule = alarmRuleRepository.save(AlarmRule.builder()
                .farmId(farmId)
                .name(request.name())
                .enabled(request.enabled() == null || request.enabled())
                .source(request.source())
                .metric(request.metric())
                .comparator(request.comparator())
                .thresholdValue(request.thresholdValue())
                .thresholdMin(request.thresholdMin())
                .thresholdMax(request.thresholdMax())
                .durationSeconds(request.durationSeconds())
                .severity(request.severity())
                .scopeType(request.scopeType())
                .scopeId(request.scopeId())
                .build());

        envThresholdAlertService.resetFarm(farmId);
        return AlarmRuleResponse.from(rule);
    }

    @Transactional
    public AlarmRuleResponse updateRule(Long farmId, Long userId, Long ruleId, AlarmRuleUpdateRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        AlarmRule rule = findRuleOrThrow(farmId, ruleId);
        rejectDerived(rule);

        // 병합된 최종 상태로 검증한다(§4.10 PATCH 규약과 동일 원칙) — 부분 수정이라 요청 값만 보면
        // "OUTSIDE_RANGE 규칙의 min만 max보다 크게 바꾸기" 같은 조합 붕괴를 놓친다.
        validateComparator(rule.getSource(), rule.getComparator(),
                request.thresholdValue() != null ? request.thresholdValue() : rule.getThresholdValue(),
                request.thresholdMin() != null ? request.thresholdMin() : rule.getThresholdMin(),
                request.thresholdMax() != null ? request.thresholdMax() : rule.getThresholdMax());

        boolean wasEnabled = rule.isEnabled();
        rule.update(request.name(), request.enabled(), request.thresholdValue(), request.thresholdMin(),
                request.thresholdMax(), request.durationSeconds(), request.severity());

        if (wasEnabled && !rule.isEnabled()) {
            // 비활성화된 규칙은 더 이상 평가되지 않아 평가 엔진의 자동 해소가 돌지 않는다 —
            // 여기서 닫지 않으면 열려 있던 알람이 사용자가 수동 처리할 때까지 유령으로 남는다.
            alarmEventService.autoResolveIfOpen(farmId, rule.metricKey());
        }
        envThresholdAlertService.resetFarm(farmId);
        return AlarmRuleResponse.from(rule);
    }

    @Transactional
    public void deleteRule(Long farmId, Long userId, Long ruleId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);
        AlarmRule rule = findRuleOrThrow(farmId, ruleId);
        rejectDerived(rule);

        // 삭제 전에 닫는다 — 삭제 후에는 metricKey를 만들 근거(규칙 id)가 사라지고, 평가 대상에서도
        // 빠져 자동 해소가 영영 돌지 않는다. 과거 이벤트 자체는 alarm_events.rule_id의
        // ON DELETE SET NULL로 감사 이력으로 보존된다(이벤트를 지우면 확인/처리 이력이 함께 사라진다).
        alarmEventService.autoResolveIfOpen(farmId, rule.metricKey());
        alarmRuleRepository.delete(rule);
        envThresholdAlertService.resetFarm(farmId);
    }

    // ── 검증 ────────────────────────────────────────────────────────────────────

    /** 소스별로 {@code metric}이 그 소스의 지표 enum 값이어야 한다(V20 ck_alarm_rules_metric의 앞단). */
    private void validateSourceAndMetric(AlarmRuleSource source, String metric) {
        if (source == AlarmRuleSource.DEVICE_HEARTBEAT) {
            if (metric != null) {
                throw new CustomException(ErrorCode.ALR003, "장비 통신 두절 규칙에는 지표를 지정할 수 없습니다.");
            }
            return;
        }
        if (metric == null) {
            throw new CustomException(ErrorCode.ALR003, "지표는 필수입니다.");
        }
        boolean valid = source == AlarmRuleSource.ENV_SNAPSHOT
                ? isEnumValue(EnvMetric.class, metric)
                : isEnumValue(SensorMetric.class, metric);
        if (!valid) {
            throw new CustomException(ErrorCode.ALR003, "이 데이터 소스에서 지원하지 않는 지표입니다: " + metric);
        }
    }

    private static <E extends Enum<E>> boolean isEnumValue(Class<E> type, String name) {
        try {
            Enum.valueOf(type, name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** comparator별로 필요한 threshold 컬럼 구성(V20 ck_alarm_rules_comparator·ck_alarm_rules_absent의 앞단). */
    private void validateComparator(AlarmRuleSource source, AlarmComparator comparator,
                                     Double value, Double min, Double max) {
        boolean heartbeat = source == AlarmRuleSource.DEVICE_HEARTBEAT;
        if ((comparator == AlarmComparator.ABSENT) != heartbeat) {
            throw new CustomException(ErrorCode.ALR003,
                    "ABSENT는 장비 통신 두절 규칙에서만, 그리고 그 규칙은 ABSENT만 쓸 수 있습니다.");
        }
        switch (comparator) {
            case GT, LT -> {
                if (value == null) {
                    throw new CustomException(ErrorCode.ALR003, "이 비교 조건에는 thresholdValue가 필요합니다.");
                }
            }
            case OUTSIDE_RANGE -> {
                if (min == null || max == null) {
                    throw new CustomException(ErrorCode.ALR003,
                            "OUTSIDE_RANGE에는 thresholdMin·thresholdMax가 모두 필요합니다.");
                }
                if (min >= max) {
                    throw new CustomException(ErrorCode.ALR003, "thresholdMin은 thresholdMax보다 작아야 합니다.");
                }
            }
            case ABSENT -> {
                // 임계값 개념이 없다 — 값이 함께 와도 저장하지 않으므로 별도 거부는 하지 않는다.
            }
        }
    }

    /**
     * 스코프 검증 — 형식(소스별 허용 스코프, FARM↔scopeId 유무)은 ALR003, <b>소속</b>은 404
     * (R001~R003, §4.10 "미소속 리소스는 존재를 유추당하지 않도록 404")로 나눈다.
     */
    private void validateScope(Long farmId, AlarmRuleSource source, AlarmScopeType scopeType, Long scopeId) {
        if (source == AlarmRuleSource.ENV_SNAPSHOT && scopeType != AlarmScopeType.FARM) {
            // env_snapshots는 farmId조차 없는 전역 단일 스트림이라 하위 스코프가 성립하지 않는다.
            throw new CustomException(ErrorCode.ALR003, "환경 스냅샷 규칙은 농장 단위 스코프만 지원합니다.");
        }
        if (scopeType == AlarmScopeType.FARM) {
            if (scopeId != null) {
                throw new CustomException(ErrorCode.ALR003, "농장 단위 스코프에는 scopeId를 지정할 수 없습니다.");
            }
            return;
        }
        if (scopeId == null) {
            throw new CustomException(ErrorCode.ALR003, "이 스코프에는 scopeId가 필요합니다.");
        }
        switch (scopeType) {
            case ZONE -> zoneRepository.findByIdAndFarmId(scopeId, farmId)
                    .orElseThrow(() -> new CustomException(ErrorCode.R001));
            case RACK -> rackRepository.findByIdAndFarmId(scopeId, farmId)
                    .orElseThrow(() -> new CustomException(ErrorCode.R002));
            case LEVEL -> rackLevelRepository.findByIdAndFarmId(scopeId, farmId)
                    .orElseThrow(() -> new CustomException(ErrorCode.R003));
            default -> throw new CustomException(ErrorCode.ALR003, "지원하지 않는 스코프입니다.");
        }
    }

    /**
     * 파생 규칙(§4.6 임계치 설정이 만든 규칙)은 이 API로 바꿀 수 없다 — 두 API가 같은 행을 서로
     * 다른 진실로 덮어쓰면 {@code PUT /env-thresholds} 다음 호출이 사용자의 규칙 편집을 조용히
     * 되돌린다(응답만 보면 성공한 것처럼 보인다).
     */
    private void rejectDerived(AlarmRule rule) {
        if (rule.getThresholdId() != null) {
            throw new CustomException(ErrorCode.ALR004);
        }
    }

    private AlarmRule findRuleOrThrow(Long farmId, Long ruleId) {
        return alarmRuleRepository.findByIdAndFarmId(ruleId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.ALR001));
    }
}
