package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 랙 — Zone 하위(contract §4.10, 이슈 #89). {@code farmId}는 테넌트 스코프 쿼리용 비정규화 컬럼
 * (멀티테넌트 룰 — zoneId만으로는 findByIdAndFarmId 패턴을 못 쓴다). {@code code}는 존 내 unique
 * (활성 행 대상 partial unique index, V14 — 위반 시 서비스가 R004로 변환).
 */
@Entity
@Table(name = "racks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE racks SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Rack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long zoneId;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false)
    private Integer levelCount;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    private Rack(Long zoneId, Long farmId, String code, Integer levelCount, Integer displayOrder) {
        this.zoneId = zoneId;
        this.farmId = farmId;
        this.code = code;
        this.levelCount = levelCount;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** PATCH 부분 수정(code·displayOrder) — null 필드는 미변경. levelCount는 캐스케이드가 필요해 별도 메서드. */
    public void update(String code, Integer displayOrder) {
        if (code != null) {
            this.code = code;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }

    /**
     * 층수 변경 반영 — 실제 {@code RackLevel} 행 생성/soft delete(장비 잔존 시 R004 거부 포함)는
     * 서비스 계층(RackService)의 책임이고, 이 메서드는 검증이 끝난 뒤 카운트 필드만 갱신한다.
     */
    public void changeLevelCount(int newLevelCount) {
        this.levelCount = newLevelCount;
    }
}
