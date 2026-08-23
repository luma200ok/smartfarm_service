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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 작업일지 — 농장 멤버가 남기는 작업 기록(contract §4.8, 이슈 #56). soft delete 없음(하드 삭제,
 * FarmController#deleteFarm의 soft delete와 달리 개별 일지는 이력 가치가 낮아 단순 삭제로 충분 — handoff).
 *
 * <p>author는 작성자 userId(FK 없음, 다른 farm-scoped 엔티티의 createdBy와 동일 패턴).
 * 데모 계정도 작성 가능(contract — DemoAccountGuard 미적용, 콘텐츠 생성은 진단·처방과 동일 원칙이며
 * 수정·삭제는 작성자 본인만 가능해 자연 격리된다).
 */
@Entity
@Table(name = "farm_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long author;

    @Column(nullable = false)
    private LocalDate logDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FarmLogType type;

    @Column(length = 1000)
    private String memo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private FarmLog(Long farmId, Long author, LocalDate logDate, FarmLogType type, String memo) {
        this.farmId = farmId;
        this.author = author;
        this.logDate = logDate;
        this.type = type;
        this.memo = memo;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** PATCH 갱신 — 작성자 본인 검증은 서비스 계층(FarmLogService)의 책임이고, 이 메서드는 전이만 캡슐화한다. */
    public void update(LocalDate logDate, FarmLogType type, String memo) {
        this.logDate = logDate;
        this.type = type;
        this.memo = memo;
    }
}
