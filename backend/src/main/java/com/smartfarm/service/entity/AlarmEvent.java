package com.smartfarm.service.entity;

import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알람 이벤트(V19, 이슈 #116) — 기존 임계치 웹훅(EnvThresholdAlertService, contract §4.6)의
 * 브리치 감지를 영속화하고 확인/처리 상태 전이를 관리한다.
 *
 * <p>상태 전이는 {@code UNACKNOWLEDGED → ACKNOWLEDGED → RESOLVED} 순서만 허용하며 전부 이
 * 엔티티 메서드로 캡슐화한다({@link #acknowledge}, {@link #resolve}, {@link #resolveAutomatically}).
 * 현재 status가 기대 전이 출발 상태가 아니면 {@link CustomException}(AL002)을 던져 이중 처리를
 * 막는다 — {@link jakarta.persistence.Version}(낙관적 락)과 이중 방어선을 이룬다(동시 acknowledge
 * 요청 중 하나는 상태 검증에서, 그보다 더 좁은 경합은 OptimisticLockException에서 막힌다).
 *
 * <p>{@code metricKey}는 멱등성 키다 — 같은 farm×metricKey 조합의 미해결(RESOLVED 아님) 이벤트가
 * 있으면 새로 만들지 않는다(DB에도 partial unique index로 2차 방어선을 둔다, V19 참고).
 * <b>#118부터 값은 {@link AlarmRule#metricKey()}(=`RULE_{ruleId}`)</b>이며, 규칙 단위로 유일해야
 * 하는 이유는 그 메서드의 javadoc에 있다. #116~#117의 옛 형식
 * ({@code "{EnvMetric}_{EnvDirection}"}, 예: {@code INDOOR_TEMP_HIGH})으로 저장된 <b>미해결</b>
 * 이벤트는 V20 마이그레이션이 새 형식으로 재매핑했고(그러지 않으면 새 평가 경로가 찾지 못해 자동
 * 해소 불가), 이미 RESOLVED된 과거 이력만 옛 형식으로 남아 있다.
 */
@Entity
@Table(name = "alarm_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private AlarmSourceType sourceType;

    @Column(name = "metric_key", nullable = false, length = 50)
    private String metricKey;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmEventStatus status;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    /** 발단이 된 임계치 설정 — 설정이 삭제되면 ON DELETE SET NULL로 null이 된다(V19). */
    @Column(name = "threshold_id")
    private Long thresholdId;

    /** 발단이 된 알람 규칙(V20, 이슈 #118) — 규칙이 삭제되면 ON DELETE SET NULL로 null이 된다. */
    @Column(name = "rule_id")
    private Long ruleId;

    /**
     * 규칙의 평가 스코프 스냅샷(V20, 이슈 #118) — 프리뷰의 위치 표기("군산1 · B3랙 4층")용.
     * V19 시절 생성된 과거 이벤트는 null이며, 그건 농장 단위로 읽는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 20)
    private AlarmScopeType scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    private AlarmEvent(Long farmId, AlarmSeverity severity, AlarmSourceType sourceType, String metricKey,
                        String message, LocalDateTime occurredAt, Long thresholdId, Long ruleId,
                        AlarmScopeType scopeType, Long scopeId) {
        this.farmId = farmId;
        this.severity = severity;
        this.sourceType = sourceType;
        this.metricKey = metricKey;
        this.message = message;
        this.status = AlarmEventStatus.UNACKNOWLEDGED;
        this.occurredAt = occurredAt;
        this.thresholdId = thresholdId;
        this.ruleId = ruleId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** UNACKNOWLEDGED → ACKNOWLEDGED. 기대 출발 상태가 아니면 AL002. */
    public void acknowledge(User user) {
        if (this.status != AlarmEventStatus.UNACKNOWLEDGED) {
            throw new CustomException(ErrorCode.AL002);
        }
        this.status = AlarmEventStatus.ACKNOWLEDGED;
        this.acknowledgedAt = LocalDateTime.now();
        this.acknowledgedBy = user.getId();
    }

    /** ACKNOWLEDGED → RESOLVED(사람 처리완료). 기대 출발 상태가 아니면 AL002. */
    public void resolve(User user) {
        if (this.status != AlarmEventStatus.ACKNOWLEDGED) {
            throw new CustomException(ErrorCode.AL002);
        }
        this.status = AlarmEventStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = user.getId();
    }

    /**
     * (UNACKNOWLEDGED|ACKNOWLEDGED) → RESOLVED — 시스템이 정상 복귀를 감지해 자동 처리한다
     * (resolvedBy=null). 이미 RESOLVED면 AL002.
     */
    public void resolveAutomatically() {
        if (this.status == AlarmEventStatus.RESOLVED) {
            throw new CustomException(ErrorCode.AL002);
        }
        this.status = AlarmEventStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = null;
    }
}
