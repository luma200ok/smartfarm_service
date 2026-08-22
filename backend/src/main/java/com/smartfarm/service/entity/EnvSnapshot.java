package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 환경 시계열 스냅샷(contract §4.6, 이슈 #52) — {@code EnvironmentSnapshotPoller}(60s)가
 * ai-server {@code GET /api/environment/today} 응답을 적재. environment/today와 동일하게 전
 * 농장 공용 단일 스트림이라 farmId를 갖지 않는다. 부분 응답을 그대로 수용하기 위해 captured_at을
 * 제외한 전 필드가 nullable(참조형).
 */
@Entity
@Table(name = "env_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnvSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @Column(name = "outdoor_temp")
    private Double outdoorTemp;

    @Column(name = "outdoor_humidity")
    private Double outdoorHumidity;

    @Column(name = "indoor_temp")
    private Double indoorTemp;

    @Column(name = "indoor_humidity")
    private Double indoorHumidity;

    private Boolean controlled;

    @Builder
    private EnvSnapshot(LocalDateTime capturedAt, Double outdoorTemp, Double outdoorHumidity,
                         Double indoorTemp, Double indoorHumidity, Boolean controlled) {
        this.capturedAt = capturedAt;
        this.outdoorTemp = outdoorTemp;
        this.outdoorHumidity = outdoorHumidity;
        this.indoorTemp = indoorTemp;
        this.indoorHumidity = indoorHumidity;
        this.controlled = controlled;
    }
}
