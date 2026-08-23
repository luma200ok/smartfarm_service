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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 양액 배합 레시피 — 농장 멤버가 저장하는 배합 계산 결과(contract §4.9, 이슈 #64). soft delete
 * 없음(FarmLog·ChatMessage와 동일하게 하드 삭제로 충분 — handoff 명시 없음, 선례 재사용).
 *
 * <p>author는 작성자 userId(FK 없음, 다른 farm-scoped 엔티티의 author/createdBy와 동일 패턴).
 * 데모 계정도 작성 가능(contract §4.9 "데모 계정: 계산·저장 허용" — DemoAccountGuard 미적용,
 * FarmLog·ChatMessage와 동일한 콘텐츠 생성 원칙. 수정·삭제는 작성자 본인(삭제는 OWNER도 가능)만
 * 가능해 데모 계정 간 상호 간섭도 자연히 격리된다.
 *
 * <p>{@code calculationSnapshot}은 저장 시점에 {@link com.smartfarm.service.service.NutrientCalculationEngine}이
 * 산출한 {@link com.smartfarm.service.dto.NutrientCalculationResponse}를 JSON 문자열로 그대로
 * 저장한 것이다(ChatMessage#sources와 동일 패턴). 조회 시 재계산하지 않고 이 스냅샷을 그대로
 * 되돌려준다 — 프리셋·알고리즘이 이후 바뀌어도 과거 레시피가 저장 당시 값을 유지해야 하기 때문
 * (양액 투입량은 실제 작물에 영향을 주는 계산이라 이력 불변성이 재계산 편의보다 우선한다).
 */
@Entity
@Table(name = "nutrient_recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NutrientRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long author;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrowthStage stage;

    @Column(nullable = false)
    private double targetN;

    @Column(nullable = false)
    private double targetP;

    @Column(nullable = false)
    private double targetK;

    @Column(nullable = false)
    private double targetCa;

    @Column(nullable = false)
    private double targetMg;

    @Column(nullable = false)
    private double targetS;

    @Column(nullable = false)
    private double tankVolumeL;

    @Column(nullable = false)
    private double concentrationFactor;

    private Double sourceWaterCa;

    private Double sourceWaterMg;

    private Double sourceWaterEc;

    /** 저장 시점 계산 결과 스냅샷(JSON) — {@link com.smartfarm.service.dto.NutrientCalculationResponse} 직렬화. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String calculationSnapshot;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private NutrientRecipe(Long farmId, Long author, String name, GrowthStage stage, double targetN,
                            double targetP, double targetK, double targetCa, double targetMg, double targetS,
                            double tankVolumeL, double concentrationFactor, Double sourceWaterCa,
                            Double sourceWaterMg, Double sourceWaterEc, String calculationSnapshot) {
        this.farmId = farmId;
        this.author = author;
        this.name = name;
        this.stage = stage;
        this.targetN = targetN;
        this.targetP = targetP;
        this.targetK = targetK;
        this.targetCa = targetCa;
        this.targetMg = targetMg;
        this.targetS = targetS;
        this.tankVolumeL = tankVolumeL;
        this.concentrationFactor = concentrationFactor;
        this.sourceWaterCa = sourceWaterCa;
        this.sourceWaterMg = sourceWaterMg;
        this.sourceWaterEc = sourceWaterEc;
        this.calculationSnapshot = calculationSnapshot;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** PATCH 갱신 — 작성자 본인 검증은 서비스 계층(NutrientService)의 책임이고, 이 메서드는 전이만 캡슐화한다. */
    public void update(String name, GrowthStage stage, double targetN, double targetP, double targetK,
                        double targetCa, double targetMg, double targetS, double tankVolumeL,
                        double concentrationFactor, Double sourceWaterCa, Double sourceWaterMg,
                        Double sourceWaterEc, String calculationSnapshot) {
        this.name = name;
        this.stage = stage;
        this.targetN = targetN;
        this.targetP = targetP;
        this.targetK = targetK;
        this.targetCa = targetCa;
        this.targetMg = targetMg;
        this.targetS = targetS;
        this.tankVolumeL = tankVolumeL;
        this.concentrationFactor = concentrationFactor;
        this.sourceWaterCa = sourceWaterCa;
        this.sourceWaterMg = sourceWaterMg;
        this.sourceWaterEc = sourceWaterEc;
        this.calculationSnapshot = calculationSnapshot;
    }
}
