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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 존×지표 목표값 — <b>존×지표당 1행</b>(V18 partial unique, contract §4.12). {@code metric}은
 * §4.11 {@link SensorMetric} 중 제어 가능한 4종({@link SensorMetric#isControllable()})만 허용한다.
 *
 * <p>존과 함께 soft delete된다(contract §4.12 캐스케이드 — {@code ControlApplyLog}는 감사 이력이라
 * 보존하지만 목표값은 존에 종속된 현재 상태다). unique는 활성 행 대상 partial index라 존 삭제 후
 * 같은 지표를 다시 설정해도 충돌하지 않는다.
 *
 * <p>목표값은 §4.11 시뮬레이터의 기저값을 대체한다(contract §4.12 시뮬레이터 연동) —
 * {@code ControlSimulationContextProvider} 참고.
 */
@Entity
@Table(name = "control_setpoints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE control_setpoints SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ControlSetpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SensorMetric metric;

    @Column(nullable = false)
    private Double targetValue;

    private Long updatedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    private ControlSetpoint(Long farmId, Long zoneId, SensorMetric metric, Double targetValue, Long updatedBy) {
        this.farmId = farmId;
        this.zoneId = zoneId;
        this.metric = metric;
        this.targetValue = targetValue;
        this.updatedBy = updatedBy;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 목표값 갱신(엔티티 캡슐화) — 같은 값 재적용도 조작자·시각을 남긴다. */
    public void changeTarget(Double newTargetValue, Long userId) {
        this.targetValue = newTargetValue;
        this.updatedBy = userId;
        this.updatedAt = LocalDateTime.now();
    }
}
