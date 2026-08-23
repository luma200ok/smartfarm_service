package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 적용 대기 큐 항목(contract §4.12) — 큐 = {@code status=PENDING} 목록. <b>서버에 저장한다</b>:
 * 프리뷰는 로컬 큐로 구현했으나 그건 목업 제약이고, 실제로는 새로고침·다중 탭·다중 사용자 사이에서
 * 큐가 보존·공유돼야 한다.
 *
 * <p>{@code fromValue}/{@code toValue}는 종류에 따라 의미가 다르므로 문자열로 저장한다 —
 * SETPOINT는 숫자 문자열("22.5", 미설정이면 {@code null}), DEVICE는 {@link DeviceStatus} 이름
 * ("NORMAL"/"OFF"). 한 컬럼에 두 타입을 담기 위한 선택이며, 해석은 적용 시점에 {@code kind}로 분기한다.
 *
 * <p>상태 전이는 엔티티 메서드로만 한다(contract §4.12 동시성 5) — {@code APPLIED}/{@code DISCARDED}는
 * 종료 상태이며 재전이는 {@link IllegalStateException}이다(호출 전에 서비스가 {@code PENDING}만
 * 골라오므로 이 예외는 프로그래밍 오류 신호다).
 */
@Entity
@Table(name = "control_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ControlChangeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SensorMetric metric;

    private Long deviceId;

    @Column(length = 50)
    private String fromValue;

    @Column(nullable = false, length = 50)
    private String toValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ControlChangeStatus status;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;

    private Long appliedBy;

    @Builder
    private ControlChange(Long farmId, Long zoneId, ControlChangeKind kind, SensorMetric metric, Long deviceId,
                           String fromValue, String toValue, Long createdBy) {
        this.farmId = farmId;
        this.zoneId = zoneId;
        this.kind = kind;
        this.metric = metric;
        this.deviceId = deviceId;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.createdBy = createdBy;
        this.status = ControlChangeStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ControlChangeStatus.PENDING;
        }
    }

    /** PENDING → APPLIED(적용 트랜잭션 안에서만 호출). 종료 상태 재전이는 금지. */
    public void markApplied(Long userId, LocalDateTime appliedAt) {
        requirePending("적용");
        this.status = ControlChangeStatus.APPLIED;
        this.appliedBy = userId;
        this.appliedAt = appliedAt;
    }

    /** PENDING → DISCARDED(개별 취소·전체 되돌리기·비상 정지·캐스케이드 폐기). 종료 상태 재전이는 금지. */
    public void markDiscarded() {
        requirePending("폐기");
        this.status = ControlChangeStatus.DISCARDED;
    }

    private void requirePending(String action) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException(
                    "이미 종료된 대기 항목은 " + action + "할 수 없습니다: id=" + id + ", status=" + status);
        }
    }
}
