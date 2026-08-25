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
 * 작물×병해충 농약 참조정보 1건(V23, 이슈 #128) — <b>실제 농촌진흥청 연동이 아니다.</b> 자체 DB에
 * 시드해둔 참고용 샘플이며, {@link #source}에 그 사실이 항상 드러나야 한다(사용자가 이 수치를 실제
 * 안전사용기준으로 믿고 살포하면 작물 피해·잔류농약 문제로 이어질 수 있다 — handoff 명시).
 *
 * <p>조회는 {@code PesticideReferenceProvider} 인터페이스 뒤에 있다 — 이 엔티티·리포지토리는 1차
 * 구현(로컬 DB)의 세부사항일 뿐, 나중에 실 API 구현체로 교체돼도 컨트롤러·응답 DTO는 그대로다.
 *
 * <p>시드 주체는 {@code PesticideReferenceSeeder}(Java initializer, idempotent) — 근거는 그
 * 클래스 주석 참고. createdAt이 없는 이유: 사용자 생성 콘텐츠가 아니라 재시드 때마다 새로 기록되는
 * 참조 데이터라 "언제 갱신됐는가"(updatedAt)만 의미가 있다.
 */
@Entity
@Table(name = "pesticide_references")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PesticideReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "crop_type", nullable = false, length = 20)
    private CropType cropType;

    /** 병해충명(예: "총채벌레", "잿빛곰팡이병"). */
    @Column(name = "pest_name", nullable = false, length = 50)
    private String pestName;

    @Column(name = "registered_product_count", nullable = false)
    private int registeredProductCount;

    /** 수확전 안전사용기간(일) — 값이 병해충 성격상 해당 없으면 null. */
    @Column(name = "pre_harvest_interval_days")
    private Integer preHarvestIntervalDays;

    @Column(length = 200)
    private String note;

    /** 출처 표기 — 반드시 "내부 샘플" 사실을 드러내는 고정 문구(seeder가 채움, §클래스 주석). */
    @Column(nullable = false, length = 200)
    private String source;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private PesticideReference(CropType cropType, String pestName, int registeredProductCount,
                                Integer preHarvestIntervalDays, String note, String source,
                                LocalDateTime updatedAt) {
        this.cropType = cropType;
        this.pestName = pestName;
        this.registeredProductCount = registeredProductCount;
        this.preHarvestIntervalDays = preHarvestIntervalDays;
        this.note = note;
        this.source = source;
        this.updatedAt = updatedAt;
    }
}
