package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알람 규칙(V20, 이슈 #118) — 농장당 N개. V10 {@code farm_env_thresholds}(농장당 1행·4컬럼)가
 * 표현하지 못하던 지표 7종·지속시간·랙 단위 스코프·등급 분화를 담는다.
 *
 * <p>{@code farmId}는 프로젝트 컨벤션대로(FarmMember·FarmEnvThreshold 등) 플레인 값이지 JPA
 * 연관관계가 아니다. soft delete를 두지 않는다 — 규칙은 사용자가 지우면 사라지는 설정이고, 그
 * 규칙이 만든 과거 알람 이벤트는 {@code alarm_events.rule_id}의 {@code ON DELETE SET NULL}로
 * 감사 이력이 보존된다.
 *
 * <p><b>파생 규칙</b>({@link #thresholdId} != null): {@code GET/PUT /env-thresholds}(계약 §4.6)가
 * 관리하는 규칙이다. 그 API가 저장될 때마다 {@code EnvThresholdService}가 <b>제자리 upsert</b>로
 * 동기화한다(삭제 후 재생성이 아니다 — id가 바뀌면 {@link #metricKey()}가 바뀌어 그 규칙으로 열려
 * 있던 알람 이벤트가 자동 해소 대상에서 사라지기 때문). alarm-rules API로는 수정/삭제할 수 없다
 * (ALR004) — 두 API가 같은 행을 서로 다른 진실로 덮어쓰는 것을 막는다.
 */
@Entity
@Table(name = "alarm_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmRule {

    /** {@code alarm_events.metric_key}(VARCHAR(50))에 쓰는 규칙 단위 키의 접두사. */
    private static final String METRIC_KEY_PREFIX = "RULE_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmRuleSource source;

    /**
     * source가 ENV_SNAPSHOT이면 {@code EnvMetric}, SENSOR_READING이면 {@link SensorMetric} 이름.
     * DEVICE_HEARTBEAT이면 null. 두 enum에 걸쳐 있어 문자열로 저장한다
     * ({@code AlarmEvent#metricKey} 선례와 동일 원칙 — 소스별로 의미가 다른 키).
     */
    @Column(length = 20)
    private String metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmComparator comparator;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "threshold_min")
    private Double thresholdMin;

    @Column(name = "threshold_max")
    private Double thresholdMax;

    /**
     * 조건이 이 시간(초) 동안 지속되면 발동한다 — 최초 이탈 시각부터의 경과 기준. 기존 "연속 2틱"
     * 하드코딩의 일반화이며, 그 이관값은 <b>60초</b>다(폴러 60s fixedDelay에서 "연속 2틱"의 실제
     * 경과는 틱 간격 1회분 — 근거는 {@code EnvThresholdService.DERIVED_DURATION_SECONDS}).
     */
    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private AlarmScopeType scopeType;

    /** FARM 스코프면 null, 그 외는 zone/rack/rackLevel id(V20 CHECK 제약으로 DB에서도 강제). */
    @Column(name = "scope_id")
    private Long scopeId;

    /** 파생 규칙 표식 — null이면 alarm-rules API로 만든 일반 규칙(클래스 주석 참고). */
    @Column(name = "threshold_id")
    private Long thresholdId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private AlarmRule(Long farmId, String name, boolean enabled, AlarmRuleSource source, String metric,
                       AlarmComparator comparator, Double thresholdValue, Double thresholdMin, Double thresholdMax,
                       Integer durationSeconds, AlarmSeverity severity, AlarmScopeType scopeType, Long scopeId,
                       Long thresholdId) {
        this.farmId = farmId;
        this.name = name;
        this.enabled = enabled;
        this.source = source;
        this.metric = metric;
        this.comparator = comparator;
        this.thresholdValue = thresholdValue;
        this.thresholdMin = thresholdMin;
        this.thresholdMax = thresholdMax;
        this.durationSeconds = durationSeconds;
        this.severity = severity;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.thresholdId = thresholdId;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * ⚠️ 멱등성 키(이슈 #118, PR #117 불변식 3) — {@code alarm_events.metric_key}에 실린다.
     *
     * <p>V19의 partial unique index {@code (farm_id, metric_key) WHERE status <> 'RESOLVED'}가
     * 멱등성 2차 방어선이므로, <b>같은 농장 안에서 두 규칙이 같은 키를 만들면 한쪽 알람이 조용히
     * 삼켜진다</b>. #116의 키였던 {@code "{EnvMetric}_{EnvDirection}"}는 규칙이 농장당 1벌일 때만
     * 유일했고, 스코프·임계값이 다른 규칙을 N개 둘 수 있게 된 지금은 충돌한다(예: "B3랙 EC 상한"과
     * "B4랙 EC 상한"이 둘 다 {@code EC_HIGH}). 규칙 id는 정의상 농장 안에서 유일하므로 이 키는
     * 스코프·지표·임계값이 무엇이든 절대 충돌하지 않는다 — 스코프를 문자열로 이어붙이는 대안
     * ({@code {source}_{metric}_{direction}_{scopeType}_{scopeId}})은 <b>같은 스코프에 같은 지표
     * 규칙을 둘 두는 것</b>(예: 경보 EC&gt;3.2 / 주의 EC&gt;2.8)을 여전히 충돌시키고 VARCHAR(50)
     * 한도에도 더 가깝다.
     */
    public String metricKey() {
        return METRIC_KEY_PREFIX + id;
    }

    /** 규칙이 만드는 알람 이벤트의 소스 구분(§4.13 {@code alarm_events.source_type}). */
    public AlarmSourceType alarmSourceType() {
        return switch (source) {
            // #116 이전부터 쌓인 환경 임계치 이벤트와 같은 값을 유지한다(기존 이벤트 이력과 연속성).
            case ENV_SNAPSHOT -> AlarmSourceType.ENV_THRESHOLD;
            case SENSOR_READING -> AlarmSourceType.SENSOR_THRESHOLD;
            case DEVICE_HEARTBEAT -> AlarmSourceType.DEVICE_HEARTBEAT;
        };
    }

    /**
     * 측정값이 이 규칙을 이탈했는가. {@link AlarmComparator#ABSENT}는 값 비교가 아니므로 여기서
     * 판정하지 않는다(호출측이 부재 여부를 직접 넘긴다).
     */
    public boolean isBreached(double value) {
        return switch (comparator) {
            case GT -> value > thresholdValue;
            case LT -> value < thresholdValue;
            case OUTSIDE_RANGE -> value < thresholdMin || value > thresholdMax;
            case ABSENT -> false;
        };
    }

    /** 알람 메시지에 실을 기준값 표현(예: {@code "> 2.8"}, {@code "40.0~80.0 범위 밖"}). */
    public String boundaryDescription() {
        return switch (comparator) {
            case GT -> "> " + thresholdValue;
            case LT -> "< " + thresholdValue;
            case OUTSIDE_RANGE -> thresholdMin + "~" + thresholdMax + " 범위 밖";
            case ABSENT -> "무응답";
        };
    }

    /** PATCH 부분 수정 — null 필드는 미변경(Zone#update와 동일 패턴). */
    public void update(String name, Boolean enabled, Double thresholdValue, Double thresholdMin,
                        Double thresholdMax, Integer durationSeconds, AlarmSeverity severity) {
        if (name != null) {
            this.name = name;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (thresholdValue != null) {
            this.thresholdValue = thresholdValue;
        }
        if (thresholdMin != null) {
            this.thresholdMin = thresholdMin;
        }
        if (thresholdMax != null) {
            this.thresholdMax = thresholdMax;
        }
        if (durationSeconds != null) {
            this.durationSeconds = durationSeconds;
        }
        if (severity != null) {
            this.severity = severity;
        }
    }

    /**
     * 파생 규칙 동기화(§4.6 {@code PUT /env-thresholds}) — <b>제자리 갱신</b>이라 id가 보존된다.
     * id가 바뀌면 {@link #metricKey()}가 바뀌어 그 규칙으로 열려 있던 알람 이벤트가 자동 해소
     * 대상에서 영영 사라진다(유령 알람).
     */
    public void syncDerived(String name, boolean enabled, Double thresholdValue) {
        this.name = name;
        this.enabled = enabled;
        this.thresholdValue = thresholdValue;
    }
}
