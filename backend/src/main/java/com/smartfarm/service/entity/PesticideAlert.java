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
 * <p>{@link PesticideReference}와 동일하게 실제 농진청 연동이 아닌 참고용 샘플이다(리뷰 P2 —
 * 최초 구현은 이 엔티티에 출처 필드를 두지 않았으나, 경보 문구가 "총채벌레 발생 밀도가 증가하고
 * 있습니다"처럼 실제 관측 기반 공식 경보로 읽혀 참조정보와 동일한 오인 위험이 있다고 판단해
 * {@link #source}를 참조정보와 같은 방식으로 추가했다).
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

    /** 출처 표기 — 반드시 "내부 샘플" 사실을 드러내는 고정 문구(seeder가 채움, §클래스 주석). */
    @Column(nullable = false, length = 500)
    private String source;

    @Builder
    private PesticideAlert(CropType cropType, String message, PesticideAlertSeverity severity,
                            LocalDateTime validFrom, LocalDateTime validUntil, String source) {
        this.cropType = cropType;
        this.message = message;
        this.severity = severity;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.source = source;
    }
}
