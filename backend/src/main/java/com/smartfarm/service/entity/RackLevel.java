package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 랙의 층 — Rack 생성 시 {@code levelCount}만큼 자동 생성된다(contract §4.10, 이슈 #89). 정수
 * 컬럼만으로 층을 표현하지 않고 별도 테이블로 둔 이유는 측정값·장비가 이 행에 FK로 매달리기
 * 때문 — 정수 컬럼이면 {@code levelNo > levelCount}를 DB가 못 막는다. {@code farmId}는
 * 비정규화(테넌트 스코프 쿼리용). {@code unique(rack_id, level_no)}는 활성 행 대상 partial unique.
 */
@Entity
@Table(name = "rack_levels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE rack_levels SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class RackLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long rackId;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Integer levelNo;

    @Column(length = 50)
    private String label;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    @Builder
    private RackLevel(Long rackId, Long farmId, Integer levelNo, String label) {
        this.rackId = rackId;
        this.farmId = farmId;
        this.levelNo = levelNo;
        this.label = label;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
