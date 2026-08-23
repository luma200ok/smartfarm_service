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
 * 센서 측정값(contract §4.11, 이슈 #90) — 가상 장비 시뮬레이터(또는 향후 실기기)가 적재하는
 * 하드 이력. {@code env_snapshots}와 달리 <b>soft delete를 하지 않는다</b> — 측정 이력은 하드
 * 보존하고 90일 보존은 별도 purge 스케줄러가 담당한다(handoff 요건 1).
 *
 * <p>⚠️ <b>위치 3종({@code zoneId}/{@code rackId}/{@code rackLevelId})은 적재 시점 device 값의
 * 스냅샷</b>이다(contract §4.11 필수 요건 1·2) — device가 나중에 다른 층으로 옮겨가도 과거 행은
 * 소급 갱신하지 않고, 조회 시 device와 join해 위치를 다시 유도하지도 않는다. device의 값을
 * <b>그대로 복사만</b> 하며 임의로 조합하지 않는다 — 조합하면 §4.10이 막은 모순 삼중조가 여기서
 * 재생산된다. 따라서 이 엔티티는 device를 참조하는 update 메서드를 두지 않는다(불변 이력).
 *
 * <p>부모(zone·rack·rack_level·device)가 이후 soft delete돼도 이 행은 정상 상태로 남는다
 * (§4.10 "측정 이력만 있으면 층 soft delete + 이력 보존" — handoff 요건 3). FK가 참조하는
 * 대상이 soft delete(deleted_at만 설정)라도 실제 행은 남아 있으므로 FK 제약 위반이 없다.
 */
@Entity
@Table(name = "sensor_readings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long deviceId;

    private Long zoneId;

    private Long rackId;

    private Long rackLevelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SensorMetric metric;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private LocalDateTime measuredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SensorSource source;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private SensorReading(Long farmId, Long deviceId, Long zoneId, Long rackId, Long rackLevelId,
                           SensorMetric metric, Double value, LocalDateTime measuredAt, SensorSource source) {
        this.farmId = farmId;
        this.deviceId = deviceId;
        this.zoneId = zoneId;
        this.rackId = rackId;
        this.rackLevelId = rackLevelId;
        this.metric = metric;
        this.value = value;
        this.measuredAt = measuredAt;
        this.source = source;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
