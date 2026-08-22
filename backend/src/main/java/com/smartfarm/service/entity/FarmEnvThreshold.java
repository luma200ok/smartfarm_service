package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 농장별 환경 임계치 설정(contract §4.6, 이슈 #52) — farm당 1행. {@code farmId}는 프로젝트
 * 컨벤션대로(FarmMember 등) 플레인 값이지 JPA 연관관계가 아니다. min/max는 전부 nullable —
 * 미설정이면 그 방향 평가를 하지 않는다(예: min만 있으면 하한 이탈만 감시).
 */
@Entity
@Table(name = "farm_env_thresholds",
        uniqueConstraints = @UniqueConstraint(name = "ux_farm_env_thresholds_farm_id", columnNames = "farm_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmEnvThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "indoor_temp_min")
    private Double indoorTempMin;

    @Column(name = "indoor_temp_max")
    private Double indoorTempMax;

    @Column(name = "indoor_humidity_min")
    private Double indoorHumidityMin;

    @Column(name = "indoor_humidity_max")
    private Double indoorHumidityMax;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private FarmEnvThreshold(Long farmId, boolean enabled, Double indoorTempMin, Double indoorTempMax,
                              Double indoorHumidityMin, Double indoorHumidityMax) {
        this.farmId = farmId;
        this.enabled = enabled;
        this.indoorTempMin = indoorTempMin;
        this.indoorTempMax = indoorTempMax;
        this.indoorHumidityMin = indoorHumidityMin;
        this.indoorHumidityMax = indoorHumidityMax;
    }

    @PrePersist
    void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }

    /** PUT 전체 교체 — 이 설정은 부분 수정 개념이 없다(contract: 요청 필드가 곧 새 상태). */
    public void replace(boolean enabled, Double indoorTempMin, Double indoorTempMax,
                         Double indoorHumidityMin, Double indoorHumidityMax) {
        this.enabled = enabled;
        this.indoorTempMin = indoorTempMin;
        this.indoorTempMax = indoorTempMax;
        this.indoorHumidityMin = indoorHumidityMin;
        this.indoorHumidityMax = indoorHumidityMax;
        this.updatedAt = LocalDateTime.now();
    }
}
