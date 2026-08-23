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
 * 존(동·구역) — Farm 하위 최상위 계층(contract §4.10, 이슈 #89). Farm 컨벤션과 동일한 soft delete.
 * 존 삭제 시 하위 랙·층도 함께 soft delete된다(서비스 계층에서 캐스케이드 — Farm처럼 JPA 컬렉션
 * cascade를 쓰지 않고 명시적 조회+delete 호출로 처리하는 이 코드베이스의 기존 관례를 따른다).
 */
@Entity
@Table(name = "zones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE zones SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    private Zone(Long farmId, String name, Integer displayOrder) {
        this.farmId = farmId;
        this.name = name;
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

    /** PATCH 부분 수정 — null 필드는 미변경(Farm#update와 동일 패턴). */
    public void update(String name, Integer displayOrder) {
        if (name != null) {
            this.name = name;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }
}
