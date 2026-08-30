package com.smartfarm.service.service;

import com.smartfarm.service.dto.AlarmAcknowledgeAllResponse;
import com.smartfarm.service.dto.AlarmEventDetailResponse;
import com.smartfarm.service.dto.AlarmEventResponse;
import com.smartfarm.service.dto.AlarmStatsResponse;
import com.smartfarm.service.dto.AlarmUnacknowledgedCountResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventLog;
import com.smartfarm.service.entity.AlarmEventLogAction;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.Rack;
import com.smartfarm.service.entity.RackLevel;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SystemLogCategory;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.entity.Zone;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.AlarmEventLogRepository;
import com.smartfarm.service.repository.AlarmEventRepository;
import com.smartfarm.service.repository.AlarmRuleRepository;
import com.smartfarm.service.repository.AlarmSeverityCountProjection;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.RackRepository;
import com.smartfarm.service.repository.UserRepository;
import com.smartfarm.service.repository.ZoneRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알람 이벤트 도메인(이슈 #116) — 사용자 대면 CRUD(list/get/acknowledge/resolve/memo/stats)와
 * 시스템 훅({@link #recordBreach}, {@link #autoResolveIfOpen} — {@link EnvThresholdAlertService}가
 * 브리치/정상 복귀 감지 시점에 호출)을 함께 둔다. 시스템 훅은 스케줄러 스레드가 호출하므로
 * (요청 사용자가 없음) 가드를 거치지 않는다.
 *
 * <p>사용자 대면 메서드의 가드는 이슈 #122로 둘로 갈렸다: <b>조회</b>(list·get·stats·
 * unacknowledgedCount)는 {@link FarmAccessGuard#requireMember}, <b>처리</b>(acknowledge·resolve·
 * acknowledgeAll·addMemo)는 {@link FarmAccessGuard#requireOperator}다 — 알람 확인/처리는 농장
 * 운영 행위라 VIEWER(조회전용)가 남의 알람 상태를 바꾸면 안 된다. 메모도 타임라인에 남는 기록이므로
 * 같은 자격을 요구한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmEventService {

    private final AlarmEventRepository alarmEventRepository;
    private final AlarmEventLogRepository alarmEventLogRepository;
    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;
    private final RackLevelRepository rackLevelRepository;
    private final AlarmRuleRepository alarmRuleRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final SystemLogService systemLogService;

    public PageResponse<AlarmEventResponse> list(Long farmId, Long userId, AlarmEventStatus status,
                                                  AlarmSeverity severity, Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<AlarmEvent> page = alarmEventRepository.search(farmId, status, severity, pageable);
        List<AlarmEventResponse> content = enrich(farmId, page.getContent());
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public AlarmEventDetailResponse get(Long farmId, Long userId, Long alarmEventId) {
        farmAccessGuard.requireMember(farmId, userId);
        AlarmEvent event = findEventOrThrow(farmId, alarmEventId);
        List<AlarmEventLog> logs = alarmEventLogRepository.findByAlarmEventIdOrderByCreatedAtAscIdAsc(event.getId());
        AlarmEventResponse response = enrichOne(farmId, event);
        return AlarmEventDetailResponse.of(response, logs);
    }

    @Transactional
    public AlarmEventResponse acknowledge(Long farmId, Long userId, Long alarmEventId) {
        farmAccessGuard.requireOperator(farmId, userId);
        User user = findUserOrThrow(userId);
        AlarmEvent event = findEventOrThrow(farmId, alarmEventId);

        event.acknowledge(user);
        alarmEventLogRepository.save(AlarmEventLog.of(event, AlarmEventLogAction.ACKNOWLEDGED, userId, null));

        return enrichOne(farmId, event);
    }

    @Transactional
    public AlarmEventResponse resolve(Long farmId, Long userId, Long alarmEventId) {
        farmAccessGuard.requireOperator(farmId, userId);
        User user = findUserOrThrow(userId);
        AlarmEvent event = findEventOrThrow(farmId, alarmEventId);

        event.resolve(user);
        alarmEventLogRepository.save(AlarmEventLog.of(event, AlarmEventLogAction.RESOLVED, userId, null));

        return enrichOne(farmId, event);
    }

    @Transactional
    public AlarmAcknowledgeAllResponse acknowledgeAll(Long farmId, Long userId) {
        farmAccessGuard.requireOperator(farmId, userId);
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
        farmAccessGuard.requireOperator(farmId, userId);
        AlarmEvent event = findEventOrThrow(farmId, alarmEventId);

        // 메모는 상태 전이를 수반하지 않는다(이슈 #116) — 타임라인에만 기록.
        alarmEventLogRepository.save(AlarmEventLog.of(event, AlarmEventLogAction.MEMO_ADDED, userId, note));

        List<AlarmEventLog> logs = alarmEventLogRepository.findByAlarmEventIdOrderByCreatedAtAscIdAsc(event.getId());
        AlarmEventResponse response = enrichOne(farmId, event);
        return AlarmEventDetailResponse.of(response, logs);
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
        // 시스템 로그 기록(이슈 #129-A, 부가 작업 — actorId는 시스템 자동 이벤트라 null). 실패해도 이
        // 트랜잭션(브리치 기록 자체)에 영향 없음(SystemLogService 참고) — #116에서 부가 작업의 예외가
        // 스케줄러 틱 전체를 날린 사례를 반복하지 않는다.
        systemLogService.record(farmId, SystemLogCategory.ALARM, "알람 이벤트가 발생했습니다: " + message, null);
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

    // ── 표시용 부가 필드 배치 조립(이슈 #135) ──────────────────────────────────

    private AlarmEventResponse enrichOne(Long farmId, AlarmEvent event) {
        return enrich(farmId, List.of(event)).get(0);
    }

    /**
     * {@code scopeLabel}/{@code ruleSummary}/{@code acknowledgedByName}/{@code resolvedByName}을
     * 목록 크기와 무관하게 <b>배치(batch) 조회</b>로 조립한다(N+1 방지 — #139에서 확립한 "id 목록을
     * 모아 IN 절로 한 번에 조회 후 Map으로 조립" 패턴을 그대로 따른다). 이 메서드가 실행하는 쿼리
     * 개수는 이벤트 건수와 무관하게 상수다: zone/rack/level 배치 조회 3개(스코프가 아예 없으면
     * 스킵) + 규칙 배치 조회 1개 + farm 단건 조회 1개 + 유저 배치 조회 1개, 최대 6개.
     *
     * <p>zone/rack/level·규칙·유저 전부 {@code findAllById}(soft delete가 있는 엔티티는
     * {@code @SQLRestriction}이 자동 적용, {@code AlarmRule}은 soft delete가 없어 삭제되면
     * 행 자체가 사라짐) — 그래서 "스코프 소멸"·"규칙 없음"·"유저 탈퇴"가 별도 분기 없이 맵에서
     * 자연히 빠지고, 아래 조립 로직은 그 빈 자리를 null로 되돌릴 뿐 "알 수 없음" 같은 문구를
     * 만들어내지 않는다(handoff 원칙).
     */
    private List<AlarmEventResponse> enrich(Long farmId, List<AlarmEvent> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        String farmName = farmRepository.findById(farmId).map(Farm::getName).orElse(null);

        List<Long> zoneScopeIds = events.stream()
                .filter(e -> e.getScopeType() == AlarmScopeType.ZONE)
                .map(AlarmEvent::getScopeId)
                .toList();
        List<Long> rackScopeIds = events.stream()
                .filter(e -> e.getScopeType() == AlarmScopeType.RACK)
                .map(AlarmEvent::getScopeId)
                .toList();
        List<Long> levelScopeIds = events.stream()
                .filter(e -> e.getScopeType() == AlarmScopeType.LEVEL)
                .map(AlarmEvent::getScopeId)
                .toList();

        Map<Long, RackLevel> levelById = levelScopeIds.isEmpty() ? Map.of()
                : rackLevelRepository.findAllById(levelScopeIds).stream()
                        .collect(Collectors.toMap(RackLevel::getId, level -> level));

        List<Long> rackIdsToFetch = new ArrayList<>(rackScopeIds);
        levelById.values().forEach(level -> rackIdsToFetch.add(level.getRackId()));
        Map<Long, Rack> rackById = rackIdsToFetch.isEmpty() ? Map.of()
                : rackRepository.findAllById(rackIdsToFetch).stream()
                        .collect(Collectors.toMap(Rack::getId, rack -> rack));

        List<Long> zoneIdsToFetch = new ArrayList<>(zoneScopeIds);
        rackById.values().forEach(rack -> zoneIdsToFetch.add(rack.getZoneId()));
        Map<Long, Zone> zoneById = zoneIdsToFetch.isEmpty() ? Map.of()
                : zoneRepository.findAllById(zoneIdsToFetch).stream()
                        .collect(Collectors.toMap(Zone::getId, zone -> zone));

        Set<Long> ruleIds = events.stream()
                .map(AlarmEvent::getRuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AlarmRule> ruleById = ruleIds.isEmpty() ? Map.of()
                : alarmRuleRepository.findAllById(ruleIds).stream()
                        .collect(Collectors.toMap(AlarmRule::getId, rule -> rule));

        Set<Long> userIds = new HashSet<>();
        events.forEach(e -> {
            if (e.getAcknowledgedBy() != null) {
                userIds.add(e.getAcknowledgedBy());
            }
            if (e.getResolvedBy() != null) {
                userIds.add(e.getResolvedBy());
            }
        });
        Map<Long, String> nicknameByUserId = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));

        return events.stream()
                .map(event -> AlarmEventResponse.from(event,
                        scopeLabel(event, farmName, zoneById, rackById, levelById),
                        ruleSummary(event, ruleById),
                        event.getAcknowledgedBy() != null ? nicknameByUserId.get(event.getAcknowledgedBy()) : null,
                        event.getResolvedBy() != null ? nicknameByUserId.get(event.getResolvedBy()) : null))
                .toList();
    }

    /**
     * 위치 표기("군산1 · B3랙 4층") 조립 — {@code scopeType}이 null이거나 FARM이면 농장명, 그 외는
     * zone/rack/level 이름을 " · "로 잇는다. ⚠️ <b>스코프 소멸 vs 빈 스코프 구분</b>(#118 선례) —
     * 스코프 대상(zone/rack/level) 자체가 삭제(또는 존재하지 않음)돼 맵에서 못 찾으면 {@code null}을
     * 반환한다. FARM 스코프로 승격하거나 농장명으로 대체하지 않는다.
     */
    private String scopeLabel(AlarmEvent event, String farmName, Map<Long, Zone> zoneById,
                               Map<Long, Rack> rackById, Map<Long, RackLevel> levelById) {
        AlarmScopeType scopeType = event.getScopeType();
        if (scopeType == null || scopeType == AlarmScopeType.FARM) {
            return farmName;
        }
        Long scopeId = event.getScopeId();
        if (scopeType == AlarmScopeType.ZONE) {
            Zone zone = zoneById.get(scopeId);
            return zone != null ? zone.getName() : null;
        }
        if (scopeType == AlarmScopeType.RACK) {
            Rack rack = rackById.get(scopeId);
            if (rack == null) {
                return null;
            }
            Zone zone = zoneById.get(rack.getZoneId());
            return joinLabelParts(zone != null ? zone.getName() : null, rack.getCode(), null);
        }
        // LEVEL
        RackLevel level = levelById.get(scopeId);
        if (level == null) {
            return null;
        }
        Rack rack = rackById.get(level.getRackId());
        Zone zone = rack != null ? zoneById.get(rack.getZoneId()) : null;
        return joinLabelParts(zone != null ? zone.getName() : null, rack != null ? rack.getCode() : null,
                level.getLabel());
    }

    private String joinLabelParts(String... parts) {
        String joined = Arrays.stream(parts)
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" · "));
        return joined.isEmpty() ? null : joined;
    }

    /**
     * 규칙 한 줄 요약(지표 · 비교연산자 · 임계값 · 지속시간) — {@code ruleId}가 없거나(수동/시스템
     * 발생 알람) 규칙이 삭제됐으면(맵에 없음) {@code null}이다. 지어낸 문구가 아니라 규칙 자체가
     * 가진 필드만 조합한다({@link AlarmRule#boundaryDescription()}·{@link AlarmComparator#label()}
     * 재사용).
     */
    private String ruleSummary(AlarmEvent event, Map<Long, AlarmRule> ruleById) {
        if (event.getRuleId() == null) {
            return null;
        }
        AlarmRule rule = ruleById.get(event.getRuleId());
        if (rule == null) {
            return null;
        }
        String metricLabel = metricLabel(rule);
        String comparatorLabel = rule.getComparator().label();
        String durationPart = (rule.getDurationSeconds() / 60) + "분 지속";
        if (rule.getComparator() == AlarmComparator.ABSENT) {
            return metricLabel + " " + comparatorLabel + " · " + durationPart;
        }
        return metricLabel + " " + comparatorLabel + " " + rule.boundaryDescription() + " · " + durationPart;
    }

    /** {@code source}에 따라 {@code metric} 문자열이 가리키는 enum이 다르다(AlarmRule 클래스 주석). */
    private String metricLabel(AlarmRule rule) {
        if (rule.getMetric() == null) {
            return "장비 응답";
        }
        return switch (rule.getSource()) {
            case ENV_SNAPSHOT -> EnvMetric.valueOf(rule.getMetric()).label();
            case SENSOR_READING -> SensorMetric.valueOf(rule.getMetric()).label();
            case DEVICE_HEARTBEAT -> "장비 응답";
        };
    }
}
