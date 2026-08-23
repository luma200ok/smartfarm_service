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

/**
 * 존 운전 모드 — <b>존당 1행</b>(V18 unique(zone_id), contract §4.12). 미설정 존은 {@code AUTO}로
 * 간주하므로 이 행은 <b>제어 조작이 처음 일어날 때 지연 생성</b>된다.
 *
 * <p>⚠️ 이 행은 동시에 <b>존 단위 직렬화의 잠금 지점</b>이다(contract §4.12 동시성 3, #91 TOCTOU
 * 교훈): 같은 존에 대한 모든 쓰기(모드 변경·큐 적재/취소·apply·비상 정지)는
 * {@code ControlModeRepository#findByZoneIdForUpdate}로 이 행을 {@code PESSIMISTIC_WRITE}
 * 잠근 뒤 진행한다. 존당 1행이라 자연스러운 잠금 지점이고, "읽고-쓰기 사이"가 전부 락 안에 들어간다.
 *
 * <p>soft delete를 두지 않는다 — 존이 soft delete되면 이 행은 도달 불가(모든 제어 표면이
 * {@code zoneRepository.findByIdAndFarmId}로 활성 존을 먼저 확인하고 미존재는 R001)해지고, zone id는
 * 재사용되지 않으므로 잔존 행이 되살아날 경로가 없다. 반대로 이 행을 지우면 잠금 지점이 사라져
 * 삭제 경로와 제어 경로가 경합할 때 락 없는 구간이 생긴다.
 */
@Entity
@Table(name = "control_modes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperationMode mode;

    private Long updatedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder
    private ControlMode(Long farmId, Long zoneId, OperationMode mode, Long updatedBy) {
        this.farmId = farmId;
        this.zoneId = zoneId;
        this.mode = mode != null ? mode : OperationMode.AUTO;
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

    /** 모드 전이(엔티티 캡슐화 — contract §4.12 동시성 5). 같은 모드로의 재설정은 no-op이 아니라
     * 조작자·시각만 갱신한다(누가 언제 확인했는지가 감사 정보). */
    public void changeMode(OperationMode newMode, Long userId) {
        this.mode = newMode;
        this.updatedBy = userId;
        // @PreUpdate는 실제 컬럼 변경이 없으면(같은 모드 재설정) 발화하지 않으므로 명시적으로 갱신한다.
        this.updatedAt = LocalDateTime.now();
    }
}
