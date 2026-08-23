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
 * 제어 적용 이력(프리뷰 "최근 적용", contract §4.12) — <b>감사 이력이므로 존·랙·장비 soft delete와
 * 무관하게 보존</b>한다(contract §4.12 캐스케이드). 대신 90일 보존 + purge
 * ({@code ControlApplyLogPurgeScheduler} — 유입량 산정 근거는 그 클래스 주석 참고).
 *
 * <p>불변 기록이라 상태 전이 메서드가 없다(생성 후 수정하지 않는다).
 */
@Entity
@Table(name = "control_apply_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlApplyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long zoneId;

    @Column(nullable = false, length = 255)
    private String summary;

    @Column(nullable = false)
    private Integer itemCount;

    private Long appliedBy;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    @Builder
    private ControlApplyLog(Long farmId, Long zoneId, String summary, Integer itemCount, Long appliedBy,
                             LocalDateTime appliedAt) {
        this.farmId = farmId;
        this.zoneId = zoneId;
        this.summary = summary;
        this.itemCount = itemCount;
        this.appliedBy = appliedBy;
        this.appliedAt = appliedAt != null ? appliedAt : LocalDateTime.now();
    }
}
