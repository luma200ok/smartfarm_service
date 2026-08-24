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
 * <p>{@code metricKey}는 소스별 의미가 다른 문자열 키다 — ENV_THRESHOLD는
 * "{@code EnvMetric}_{@code EnvDirection}"(예: {@code INDOOR_TEMP_HIGH}) 형태로 farm×항목×방향
 * 조합을 표현하며, 같은 조합의 미해결(RESOLVED 아님) 이벤트가 있으면 새로 만들지 않는다(멱등성 —
 * DB에도 partial unique index로 2차 방어선을 둔다, V19 참고).
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    private AlarmEvent(Long farmId, AlarmSeverity severity, AlarmSourceType sourceType, String metricKey,
                        String message, LocalDateTime occurredAt, Long thresholdId) {
        this.farmId = farmId;
        this.severity = severity;
        this.sourceType = sourceType;
        this.metricKey = metricKey;
        this.message = message;
        this.status = AlarmEventStatus.UNACKNOWLEDGED;
        this.occurredAt = occurredAt;
        this.thresholdId = thresholdId;
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
