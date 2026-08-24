package com.smartfarm.service.service;

import com.smartfarm.service.dto.AlarmAcknowledgeAllResponse;
import com.smartfarm.service.dto.AlarmEventDetailResponse;
import com.smartfarm.service.dto.AlarmEventResponse;
import com.smartfarm.service.dto.AlarmStatsResponse;
import com.smartfarm.service.dto.AlarmUnacknowledgedCountResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventLog;
import com.smartfarm.service.entity.AlarmEventLogAction;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.AlarmEventLogRepository;
import com.smartfarm.service.repository.AlarmEventRepository;
import com.smartfarm.service.repository.AlarmSeverityCountProjection;
import com.smartfarm.service.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알람 이벤트 도메인(이슈 #116) — 사용자 대면 CRUD(list/get/acknowledge/resolve/memo/stats)와
 * 시스템 훅({@link #recordBreach}, {@link #autoResolveIfOpen} — {@link EnvThresholdAlertService}가
 * 브리치/정상 복귀 감지 시점에 호출)을 함께 둔다. 사용자 대면 메서드는 전부
 * {@link FarmAccessGuard#requireMember}로 farm 스코프를 검증하지만, 시스템 훅은 스케줄러 스레드가
 * 호출하므로(요청 사용자가 없음) 가드를 거치지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmEventService {

    private final AlarmEventRepository alarmEventRepository;
    private final AlarmEventLogRepository alarmEventLogRepository;
    private final UserRepository userRepository;
    private final FarmAccessGuard farmAccessGuard;

    public PageResponse<AlarmEventResponse> list(Long farmId, Long userId, AlarmEventStatus status,
                                                  AlarmSeverity severity, Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<AlarmEventResponse> page = alarmEventRepository.search(farmId, status, severity, pageable)
                .map(AlarmEventResponse::from);
        return PageResponse.of(page);
    }

    public AlarmEventDetailResponse get(Long farmId, Long userId, Long alarmEventId) {
        farmAccessGuard.requireMember(farmId, userId);
        AlarmEvent event = findEventOrThrow(farmId, alarmEventId);
        List<AlarmEventLog> logs = alarmEventLogRepository.findByAlarmEventIdOrderByCreatedAtAscIdAsc(event.getId());
        return AlarmEventDetailResponse.of(event, logs);
    }

    @Transactional
    public AlarmEventResponse acknowledge(Long farmId, Long userId, Long alarmEventId) {
        farmAccessGuard.requireMember(farmId, userId);
        User user = findUserOrThrow(userId);
        AlarmEvent event = findEventOrThrow(farmId, alarmEventId);

        event.acknowledge(user);
        alarmEventLogRepository.save(AlarmEventLog.of(event, AlarmEventLogAction.ACKNOWLEDGED, userId, null));

        return AlarmEventResponse.from(event);
    }

    @Transactional
    public AlarmEventResponse resolve(Long farmId, Long userId, Long alarmEventId) {
        farmAccessGuard.requireMember(farmId, userId);
        User user = findUserOrThrow(userId);
        AlarmEvent event = findEventOrThrow(farmId, alarmEventId);

        event.resolve(user);
        alarmEventLogRepository.save(AlarmEventLog.of(event, AlarmEventLogAction.RESOLVED, userId, null));

        return AlarmEventResponse.from(event);
    }

    @Transactional
    public AlarmAcknowledgeAllResponse acknowledgeAll(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        User user = findUserOrThrow(userId);

        List<AlarmEvent> events = alarmEventRepository.findByFarmIdAndStatus(farmId,
                AlarmEventStatus.UNACKNOWLEDGED);
        for (AlarmEvent event : events) {
            event.acknowledge(user);
            alarmEventLogRepository.save(AlarmEventLog.of(event, AlarmEventLogAction.ACKNOWLEDGED, userId, null));
        }

        return new AlarmAcknowledgeAllResponse(events.size());
    }

    @Transactional
    public AlarmEventDetailResponse addMemo(Long farmId, Long userId, Long alarmEventId, String note) {
        farmAccessGuard.requireMember(farmId, userId);
        AlarmEvent event = findEventOrThrow(farmId, alarmEventId);

        // 메모는 상태 전이를 수반하지 않는다(이슈 #116) — 타임라인에만 기록.
        alarmEventLogRepository.save(AlarmEventLog.of(event, AlarmEventLogAction.MEMO_ADDED, userId, note));

        List<AlarmEventLog> logs = alarmEventLogRepository.findByAlarmEventIdOrderByCreatedAtAscIdAsc(event.getId());
        return AlarmEventDetailResponse.of(event, logs);
    }

    /**
     * severity별 건수·평균 확인 소요시간을 전량 로딩 없이 DB에서 직접 집계한다(이슈 #116 리뷰
     * P2-C). 컨트롤러의 {@code @Min(1) @Max(90)}가 days 범위를 이미 검증하므로 여기서는 그 값을
     * 그대로 신뢰해 since를 계산한다.
     */
    public AlarmStatsResponse stats(Long farmId, Long userId, int days) {
        farmAccessGuard.requireMember(farmId, userId);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        Map<AlarmSeverity, Long> countBySeverity = new EnumMap<>(AlarmSeverity.class);
        for (AlarmSeverityCountProjection row : alarmEventRepository.countBySeverityAfter(farmId, since)) {
            // severity 컬럼은 DB에 문자열로 저장돼 있어(@Enumerated(STRING)) enum 재정의·수기 데이터
            // 등으로 알 수 없는 값이 들어와도 valueOf가 IllegalArgumentException으로 500을 유발하지
            // 않도록 방어한다(이슈 #116 리뷰 P3) — 그 행만 건너뛰고 나머지 severity 집계는 정상 반환.
            try {
                countBySeverity.put(AlarmSeverity.valueOf(row.getSeverity()), row.getEventCount());
            } catch (IllegalArgumentException e) {
                log.warn("stats 집계 중 알 수 없는 severity 값 건너뜀: farmId={}, severity={}",
                        farmId, row.getSeverity());
            }
        }
        Double avgAcknowledgeMinutes = alarmEventRepository.avgAcknowledgeMinutesAfter(farmId, since);

        return AlarmStatsResponse.of(days, countBySeverity, avgAcknowledgeMinutes);
    }

    public AlarmUnacknowledgedCountResponse unacknowledgedCount(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        long count = alarmEventRepository.countByFarmIdAndStatus(farmId, AlarmEventStatus.UNACKNOWLEDGED);
        return new AlarmUnacknowledgedCountResponse(count);
    }

    // ── 시스템 훅(EnvThresholdAlertService 전용, 사용자 컨텍스트 없음) ──────────────

    /**
     * 브리치 감지 시 알람 이벤트를 생성한다 — 멱등: 같은 farm×metricKey 조합의 미해결 이벤트가
     * 이미 있으면 아무 것도 하지 않는다(이슈 #116). 1차 방어선(이 조회)과 DB partial unique
     * index(V19, 2차 방어선)가 이중으로 중복 생성을 막는다.
     *
     * <p>{@code rule}(이슈 #118)은 발단 규칙이다 — 이벤트에 {@code ruleId}·{@code scopeType}·
     * {@code scopeId}(프리뷰의 위치 표기용)와 파생 규칙이면 {@code thresholdId}(§4.6 하위호환)를
     * 함께 남긴다. null이면 규칙 밖에서 온 브리치로 보고 그 필드들을 비운다(테스트·향후 소스용).
     */
    @Transactional
    public void recordBreach(Long farmId, AlarmSeverity severity, AlarmSourceType sourceType, String metricKey,
                              String message, LocalDateTime occurredAt, AlarmRule rule) {
        if (alarmEventRepository.findOpenEventByFarmAndMetric(farmId, metricKey).isPresent()) {
            return;
        }
        AlarmEvent event = alarmEventRepository.save(AlarmEvent.builder()
                .farmId(farmId)
                .severity(severity)
                .sourceType(sourceType)
                .metricKey(metricKey)
                .message(message)
                .occurredAt(occurredAt)
                .thresholdId(rule != null ? rule.getThresholdId() : null)
                .ruleId(rule != null ? rule.getId() : null)
                .scopeType(rule != null ? rule.getScopeType() : null)
                .scopeId(rule != null ? rule.getScopeId() : null)
                .build());
        alarmEventLogRepository.save(AlarmEventLog.of(event, AlarmEventLogAction.CREATED, null, null));
    }

    /**
     * 정상 복귀 감지 시 열린 이벤트가 있으면 시스템이 자동으로 RESOLVED 전이한다(resolvedBy=null,
     * 이슈 #116). 열린 이벤트가 없으면 조용히 무시(no-op) — 매 정상 틱마다 호출돼도 안전하다.
     */
    @Transactional
    public void autoResolveIfOpen(Long farmId, String metricKey) {
        alarmEventRepository.findOpenEventByFarmAndMetric(farmId, metricKey)
                .ifPresent(event -> {
                    event.resolveAutomatically();
                    alarmEventLogRepository.save(
                            AlarmEventLog.of(event, AlarmEventLogAction.RESOLVED, null, "자동 해소"));
                });
    }

    private AlarmEvent findEventOrThrow(Long farmId, Long alarmEventId) {
        return alarmEventRepository.findByIdAndFarmId(alarmEventId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.AL001));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));
    }
}
