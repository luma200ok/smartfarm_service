package com.smartfarm.service.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
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
 * 거부). 깊은 쪽이 주어지면 서비스 계층이 상위를 유도해 함께 저장한다(부모 FK 자동 채움, 2026-08-23
 * 사이클 2 — 저장되는 삼중조는 항상 완전하다). {@code serial}은 농장 스코프 partial unique(활성
 * 행)이며 null 허용(V14).
 *
 * <p>{@code metrics}(V16, 사이클 2) — {@code kind=SENSOR}는 측정 지표를 1개 이상 선언해야 하고
 * {@code CONTROLLER}/{@code GATEWAY}는 비워야 한다(서비스 계층이 C001로 강제). 시뮬레이터는 이
 * 선언분만 생성한다 — 초판이 센서 1대를 7종 복합 프로브로 가정해 적재량 추정이 7배 틀어졌던 계약
 * 결함을 여기서 바로잡는다.
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

    /**
     * 이 장비가 측정하는 지표 선언(contract §4.10 사이클 2, V16 {@code device_metrics}). SENSOR는
     * 1개 이상, 그 외는 비어야 한다(DeviceService 검증). soft delete와 무관하게 device_id FK로만
     * 연결되는 자식 테이블이라 device가 soft delete돼도 이 행들은 그대로 남는다(하드 삭제 없음 —
     * 이력 조회 시 과거 장비의 측정 지표를 알 수 있어야 하므로 의도된 동작).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "device_metrics", joinColumns = @JoinColumn(name = "device_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "metric", length = 20)
    private Set<SensorMetric> metrics = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    private Device(Long farmId, Long zoneId, Long rackId, Long rackLevelId, String name, DeviceKind kind,
                    String model, String serial, DeviceStatus status, LocalDateTime calibrationDueAt,
                    LocalDate installedOn, Set<SensorMetric> metrics) {
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
        this.metrics = metrics != null ? new LinkedHashSet<>(metrics) : new LinkedHashSet<>();
    }

    /**
     * {@code @Getter}가 자동 생성하는 getter는 내부 mutable {@code Set}을 그대로 반환한다 — 호출측이
     * 반환값을 {@code add}/{@code remove}하면 영속 컬렉션이 검증을 거치지 않고 바뀐다(사이클 2
     * 리뷰 P3-4). 읽기 전용 뷰로 감싸 노출한다 — 뷰이므로 {@link #update}가 내부 Set을 갱신하면
     * (clear+addAll) 그대로 반영된다(복사본이 아니다).
     */
    public Set<SensorMetric> getMetrics() {
        return Collections.unmodifiableSet(metrics);
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
     * 제어 적용·비상 정지에 의한 상태 전이(contract §4.12) — {@link #update}는 PATCH 전용(부분 수정)
     * 이라 제어 경로가 쓰기엔 넓다. 상태 하나만 바꾸는 좁은 진입점을 따로 둬, 제어가 실수로 위치·
     * 지표 선언까지 건드릴 수 없게 한다. 호출측({@code ControlService})이 전이 가능 여부(통신 두절
     * 장비 제외 등)를 이미 판정한 뒤 부른다.
     */
    public void changeStatus(DeviceStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * PATCH 부분 수정 — null 필드는 미변경(Farm#update와 동일 패턴). 위치 FK 3종도 동일 원칙이라
     * 전체 해제(모두 null로 되돌리기)는 1차 미지원(FarmUpdateRequest의 location 미지원과 동일 원칙).
     * 위치 3종은 호출측(DeviceService)이 부모 FK 자동 채움까지 반영한 <b>해석된 최종값</b>을
     * 넘긴다 — 이 메서드 자체는 자동 채움을 모른다(단순 대입).
     *
     * <p>{@code metrics}는 null이면 미변경, non-null(빈 Set 포함)이면 전체 교체다 — 부분 추가/삭제가
     * 아니라 선언 목록 전체를 새로 지정하는 방식(location 필드들과 다른 이유: metrics는 "집합"이라
     * 개별 null 판정이 없다. 호출측이 검증을 마친 뒤 넘긴다).
     */
    public void update(Long zoneId, Long rackId, Long rackLevelId, String name, DeviceKind kind, String model,
                        String serial, DeviceStatus status, LocalDateTime calibrationDueAt, LocalDate installedOn,
                        Set<SensorMetric> metrics) {
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
        if (metrics != null) {
            this.metrics.clear();
            this.metrics.addAll(metrics);
        }
    }
}
