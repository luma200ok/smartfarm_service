package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 작물별 병해충 발생주의 경보 1건(V23, 이슈 #128) — "이번 주 발생 주의" 성격이라 유효기간
 * ({@link #validFrom}~{@link #validUntil})이 필수다. 기간이 지난 경보는 조회에서 제외돼야 하며
 * 그 필터는 {@code PesticideReferenceProvider} 구현체(1차=로컬 DB)가 담당한다.
 *
 * <p>{@link PesticideReference}와 동일하게 실제 농진청 연동이 아닌 참고용 샘플이다 — 이 엔티티
 * 자체에는 출처 필드가 없지만(경보는 "우리 서비스가 안내하는 주의" 성격이라 참조정보만큼 출처
 * 신뢰도 문제가 크지 않다고 판단), 응답 DTO 차원에서 참조정보와 동일한 disclaimer가 필요하면
 * {@code PesticideReferenceService} 계층에서 보강한다.
 */
@Entity
@Table(name = "pesticide_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PesticideAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "crop_type", nullable = false, length = 20)
    private CropType cropType;

    @Column(nullable = false, length = 200)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PesticideAlertSeverity severity;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @Builder
    private PesticideAlert(CropType cropType, String message, PesticideAlertSeverity severity,
                            LocalDateTime validFrom, LocalDateTime validUntil) {
        this.cropType = cropType;
        this.message = message;
        this.severity = severity;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }
}
