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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 장비/센서 — 개별 장비 단위로 저장한다(contract §4.10, 이슈 #89). 제품군 집계는 서버가
 * {@code DeviceSummaryResponse}에서 수행하고, 저장은 개체 단위(보정주기·최종수신·통신상태가
 * 개체 속성이라 집계 저장은 정보 손실).
 *
 * <p>위치 FK 3종({@code zoneId}/{@code rackId}/{@code rackLevelId})은 전부 nullable — 게이트웨이는
 * 존 단위, 센서는 층 단위로 서로 다르게 매달린다. 최소 하나는 필수(전부 null이면 서비스가 C001로
 * 거부). {@code serial}은 농장 스코프 partial unique(활성 행)이며 null 허용(V14).
 */
@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE devices SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    private Long zoneId;

    private Long rackId;

    private Long rackLevelId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceKind kind;

    @Column(length = 50)
    private String model;

    @Column(length = 50)
    private String serial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status;

    private LocalDateTime lastSeenAt;

    private LocalDateTime calibrationDueAt;

    private LocalDate installedOn;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    private Device(Long farmId, Long zoneId, Long rackId, Long rackLevelId, String name, DeviceKind kind,
                    String model, String serial, DeviceStatus status, LocalDateTime calibrationDueAt,
                    LocalDate installedOn) {
        this.farmId = farmId;
        this.zoneId = zoneId;
        this.rackId = rackId;
        this.rackLevelId = rackLevelId;
        this.name = name;
        this.kind = kind;
        this.model = model;
        this.serial = serial;
        this.status = status != null ? status : DeviceStatus.NORMAL;
        this.calibrationDueAt = calibrationDueAt;
        this.installedOn = installedOn;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * PATCH 부분 수정 — null 필드는 미변경(Farm#update와 동일 패턴). 위치 FK 3종도 동일 원칙이라
     * 전체 해제(모두 null로 되돌리기)는 1차 미지원(FarmUpdateRequest의 location 미지원과 동일 원칙).
     */
    public void update(Long zoneId, Long rackId, Long rackLevelId, String name, DeviceKind kind, String model,
                        String serial, DeviceStatus status, LocalDateTime calibrationDueAt, LocalDate installedOn) {
        if (zoneId != null) {
            this.zoneId = zoneId;
        }
        if (rackId != null) {
            this.rackId = rackId;
        }
        if (rackLevelId != null) {
            this.rackLevelId = rackLevelId;
        }
        if (name != null) {
            this.name = name;
        }
        if (kind != null) {
            this.kind = kind;
        }
        if (model != null) {
            this.model = model;
        }
        if (serial != null) {
            this.serial = serial;
        }
        if (status != null) {
            this.status = status;
        }
        if (calibrationDueAt != null) {
            this.calibrationDueAt = calibrationDueAt;
        }
        if (installedOn != null) {
            this.installedOn = installedOn;
        }
    }
}
