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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 스케줄·자동화 규칙 골격(V25, 이슈 #129-C) — 프리뷰에 전용 화면이 없고 디자인이 미확정이라
 * <b>데이터모델·CRUD 골격만</b> 구현한다(사용자 결정).
 *
 * <p>⚠️ <b>이 엔티티는 "저장"만 한다 — 실행하지 않는다.</b> {@code @Scheduled} 트리거나 저장된
 * {@code cronExpression}/{@code actionPayload}를 실제로 평가·수행하는 경로는 이 이슈의 범위 밖이다.
 * 디자인이 미확정인 상태에서 실행 로직까지 만들면 나중에 화면이 정해졌을 때 다시 설계해야 해
 * 버려질 가능성이 크다 — 실행은 스케줄러가 붙는 후속 이슈에서 다룬다.
 *
 * <p>{@code cronExpression}은 저장 시점에 {@code CronExpression.parse}로 형식만 검증한다
 * ({@code ScheduleService} 참고) — 잘못된 식이 저장되면 나중에 스케줄러가 붙을 때 그제서야 터진다.
 *
 * <p>{@code actionPayload}는 {@code SavedAnalysis#metrics}(V22)와 동일하게 JSONB 문자열 컬럼이다 —
 * {@code actionType}별로 필요한 파라미터 형태가 달라(예: DEVICE_ON/OFF는 deviceId, SETPOINT_CHANGE는
 * metric+value) 골격 단계에서 컬럼을 고정하지 않는다.
 */
@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    /** 존 단위로 좁힐 때만 지정(nullable) — 농장 전체 대상 스케줄은 null. */
    @Column(name = "zone_id")
    private Long zoneId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ScheduleActionType actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_payload", columnDefinition = "jsonb")
    private String actionPayload;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Schedule(Long farmId, Long zoneId, String name, boolean enabled, String cronExpression,
                      ScheduleActionType actionType, String actionPayload, Long createdBy) {
        this.farmId = farmId;
        this.zoneId = zoneId;
        this.name = name;
        this.enabled = enabled;
        this.cronExpression = cronExpression;
        this.actionType = actionType;
        this.actionPayload = actionPayload;
        this.createdBy = createdBy;
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
     * PATCH 부분 수정 — {@code zoneId}·{@code actionType}은 정체성이라 대상이 아니다
     * (AlarmRule#update와 동일 원칙 — 바꾸려면 새로 만들고 기존을 지운다). {@code null} 인자는
     * 미변경.
     */
    public void update(String name, Boolean enabled, String cronExpression, String actionPayload) {
        if (name != null) {
            this.name = name;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (cronExpression != null) {
            this.cronExpression = cronExpression;
        }
        if (actionPayload != null) {
            this.actionPayload = actionPayload;
        }
    }
}
