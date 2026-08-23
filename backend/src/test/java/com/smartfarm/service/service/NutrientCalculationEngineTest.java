package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import com.smartfarm.service.dto.NutrientCalculationResponse;
import com.smartfarm.service.dto.NutrientSourceWaterRequest;
import com.smartfarm.service.dto.NutrientTankAllocationResponse;
import com.smartfarm.service.dto.NutrientTargetRequest;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배합 계산 엔진 검산 테스트(이슈 #64) — "비료 투입량은 틀리면 실제 작물이 죽는 계산"이라 프리셋
 * 4단계 전부의 투입량·EC·이온 밸런스 기대값을 손계산(scratchpad/nutrient_calc.py로 IUPAC
 * 원자량으로부터 독립 재계산해 교차검증)으로 고정한다. tankVolumeL=1·concentrationFactor=1
 * (계약 허용 최소 경계값)을 써서 ppm=g 환산이 단순해지는 값으로 검증하고, 스케일 불변성은
 * {@link #scalingIsLinearInMass()}에서 별도로 확인한다.
 */
class NutrientCalculationEngineTest {

    private final NutrientCalculationEngine engine = new NutrientCalculationEngine();

    private static NutrientTargetRequest target(double n, double p, double k, double ca, double mg, double s) {
        return new NutrientTargetRequest(n, p, k, ca, mg, s);
    }

    private NutrientTankAllocationResponse tank(NutrientCalculationResponse response, String tank) {
        return response.tanks().stream().filter(t -> t.tank().equals(tank)).findFirst().orElseThrow();
    }

    // ── 프리셋 4단계 검산(출처: NutrientPresets 클래스 주석 — OSU Extension HYG-1437) ──────────

    @Test
    @DisplayName("SEEDLING: Ca(NO3)2 단독으로 N 초과 공급 — KNO3 미사용, N·S 경고 발생(이온밸런스는 30% 미만이라 없음)")
    void seedlingPreset() {
        NutrientCalculationResponse result = engine.calculate(target(90, 47, 144, 160, 60, 79), 1.0, 1.0, null);

        NutrientTankAllocationResponse tankA = tank(result, "A");
        assertThat(tankA.items()).hasSize(1); // 질산칼슘 4수염만 — N이 이미 초과라 질산칼륨 불필요
        assertThat(tankA.items().get(0).formula()).isEqualTo("Ca(NO3)2·4H2O");
        assertThat(tankA.items().get(0).amountG()).isCloseTo(0.94, offset(0.01));

        NutrientTankAllocationResponse tankB = tank(result, "B");
        assertThat(tankB.items()).hasSize(3); // MKP, MgSO4, K2SO4
        assertThat(tankB.items().get(0).amountG()).isCloseTo(0.21, offset(0.01)); // MKP
        assertThat(tankB.items().get(1).amountG()).isCloseTo(0.61, offset(0.01)); // MgSO4
        assertThat(tankB.items().get(2).amountG()).isCloseTo(0.19, offset(0.01)); // K2SO4(K 부족분)

        assertThat(result.estimatedEc()).isCloseTo(1.66, offset(0.01));
        assertThat(result.ionBalance().cationMeL()).isCloseTo(16.60, offset(0.01));
        assertThat(result.ionBalance().anionMeL()).isCloseTo(12.87, offset(0.01));
        assertThat(result.ionBalance().deviationPercent()).isCloseTo(25.33, offset(0.05));

        // N(초과 공급)·S(K2SO4 부산물 초과) 2건 경고. 이온밸런스 편차(25.33%)는 임계값(30%,
        // contract §4.9 2026-08-23 정정) 미만이라 경고가 뜨지 않는다.
        assertThat(result.warnings()).hasSize(2);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).startsWith("N "));
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).startsWith("S "));
        assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).contains("이온 밸런스"));
    }

    @Test
    @DisplayName("VEGETATIVE: KNO3 소량 추가로 N 정확히 충족, K2SO4로 K 정확히 충족 — S 경고만 남는다")
    void vegetativePreset() {
        NutrientCalculationResponse result = engine.calculate(target(120, 47, 210, 169, 60, 79), 1.0, 1.0, null);

        NutrientTankAllocationResponse tankA = tank(result, "A");
        assertThat(tankA.items()).hasSize(2); // 질산칼슘 4수염 + 질산칼륨
        assertThat(tankA.items().get(0).amountG()).isCloseTo(1.00, offset(0.01));
        assertThat(tankA.items().get(1).formula()).isEqualTo("KNO3");
        assertThat(tankA.items().get(1).amountG()).isCloseTo(0.01, offset(0.01));

        NutrientTankAllocationResponse tankB = tank(result, "B");
        assertThat(tankB.items()).hasSize(3);
        assertThat(tankB.items().get(2).amountG()).isCloseTo(0.32, offset(0.01)); // K2SO4

        assertThat(result.estimatedEc()).isCloseTo(1.87, offset(0.01));
        assertThat(result.ionBalance().deviationPercent()).isCloseTo(22.09, offset(0.05));

        // N은 정확히 목표에 맞춰지므로(잔여분을 KNO3로 정확히 채움) N 경고 없음. 이온밸런스
        // 편차(22.09%)도 임계값(30%) 미만이라 뜨지 않는다 — S 경고만 남는다.
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).startsWith("N "));
        assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).contains("이온 밸런스"));
    }

    @Test
    @DisplayName("FRUITING: N·K 모두 정확히 충족(KNO3 대량 필요), S 경고만 남는다")
    void fruitingPreset() {
        NutrientCalculationResponse result = engine.calculate(target(165, 47, 342, 169, 60, 79), 1.0, 1.0, null);

        NutrientTankAllocationResponse tankA = tank(result, "A");
        assertThat(tankA.items()).hasSize(2);
        assertThat(tankA.items().get(1).amountG()).isCloseTo(0.34, offset(0.01)); // KNO3

        assertThat(result.estimatedEc()).isCloseTo(2.21, offset(0.01));
        // 이온밸런스 편차(19.3%)도 임계값(30%) 미만이라 S 경고만 남는다.
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).startsWith("N "));
        assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).startsWith("K "));
        assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).contains("이온 밸런스"));
    }

    @Test
    @DisplayName("HARVEST: Ca 목표가 가장 높아 질산칼슘 4수염 투입량도 최대")
    void harvestPreset() {
        NutrientCalculationResponse result = engine.calculate(target(190, 47, 350, 200, 60, 79), 1.0, 1.0, null);

        NutrientTankAllocationResponse tankA = tank(result, "A");
        assertThat(tankA.items().get(0).amountG()).isCloseTo(1.18, offset(0.01));
        assertThat(tankA.items().get(1).amountG()).isCloseTo(0.36, offset(0.01));

        assertThat(result.estimatedEc()).isCloseTo(2.39, offset(0.01));
        assertThat(result.ionBalance().deviationPercent()).isCloseTo(17.59, offset(0.05));
    }

    // ── 원수 보정 ──────────────────────────────────────────────

    @Test
    @DisplayName("원수 Ca 보정 시 질산칼슘 4수염 투입량이 줄고 N 경고가 사라진다(목표에 정확히 맞춰짐)")
    void sourceWaterCorrectionReducesDosage() {
        NutrientCalculationResponse withoutCorrection = engine.calculate(target(90, 47, 144, 160, 60, 79), 1.0, 1.0,
                null);
        NutrientCalculationResponse withCorrection = engine.calculate(target(90, 47, 144, 160, 60, 79), 1.0, 1.0,
                new NutrientSourceWaterRequest(60.0, null, null));

        double caNitrateWithout = tank(withoutCorrection, "A").items().get(0).amountG();
        double caNitrateWith = tank(withCorrection, "A").items().get(0).amountG();
        assertThat(caNitrateWith).isLessThan(caNitrateWithout);
        assertThat(caNitrateWith).isCloseTo(0.59, offset(0.01));

        // 보정 후에는 Ca(NO3)2가 덜 들어가 N도 덜 딸려오므로 질산칼륨이 필요해지고, N은 정확히
        // 목표(90ppm)에 맞춰진다 — 원수 보정 전(SEEDLING 기본)에 있던 N 경고가 사라진다.
        assertThat(withCorrection.warnings()).noneSatisfy(w -> assertThat(w).startsWith("N "));
        assertThat(withCorrection.ionBalance().deviationPercent()).isCloseTo(5.58, offset(0.05));
    }

    @Test
    @DisplayName("원수 Ca가 목표 Ca를 초과하면(투입량 음수) N003 — 초과 성분을 메시지에 명시한다")
    void sourceWaterCalciumExceedingTargetThrowsN003() {
        assertThatThrownBy(() -> engine.calculate(target(90, 47, 144, 160, 60, 79), 1.0, 1.0,
                new NutrientSourceWaterRequest(200.0, null, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.N003);
                    assertThat(ce.getMessage()).contains("Ca");
                });
    }

    @Test
    @DisplayName("원수 Mg가 목표 Mg를 초과하면(투입량 음수) N003 — 초과 성분을 메시지에 명시한다")
    void sourceWaterMagnesiumExceedingTargetThrowsN003() {
        assertThatThrownBy(() -> engine.calculate(target(90, 47, 144, 160, 60, 79), 1.0, 1.0,
                new NutrientSourceWaterRequest(null, 100.0, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.N003);
                    assertThat(ce.getMessage()).contains("Mg");
                });
    }

    @Test
    @DisplayName("원수 Ca·Mg가 모두 목표를 초과하면 메시지에 두 성분이 함께 명시된다")
    void sourceWaterBothExceedingTargetListsBothInMessage() {
        assertThatThrownBy(() -> engine.calculate(target(90, 47, 144, 160, 60, 79), 1.0, 1.0,
                new NutrientSourceWaterRequest(200.0, 100.0, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getMessage()).contains("Ca").contains("Mg");
                });
    }

    // ── 목표 0인 성분의 부산물 경고(0으로 나누기는 피하면서 절대 ppm 기준으로 경고) ──────────────

    @Test
    @DisplayName("N 목표가 0이어도 Ca(NO3)2가 딸려 보내는 N이 있으면 절대 ppm 기준 경고가 뜬다")
    void zeroTargetWithByproductTriggersAbsoluteWarning() {
        // N=0·P=0·K=0, Ca=160만 요청 — Ca(NO3)2로 Ca를 채우면 N이 부산물로 딸려온다(그런데 N
        // 목표는 0이라 비율(%) 계산은 무의미해 절대 ppm 기준 경고여야 한다).
        NutrientCalculationResponse result = engine.calculate(target(0, 0, 0, 160, 0, 0), 1.0, 1.0, null);

        NutrientTankAllocationResponse tankA = tank(result, "A");
        assertThat(tankA.items()).hasSize(1); // 질산칼슘 4수염만(N 목표가 0이라 KNO3는 추가되지 않음)

        assertThat(result.warnings()).anySatisfy(w -> {
            assertThat(w).startsWith("N 목표는 0ppm이지만");
            assertThat(w).contains("111."); // nDeliveredPpm ≈ 111.83(SEEDLING과 동일 — Ca 목표가 같음)
        });
    }

    @Test
    @DisplayName("목표가 전부 0이면(부산물도 없음) 경고가 뜨지 않는다(0으로 나누기 회피 확인)")
    void allZeroTargetsProduceNoWarnings() {
        NutrientCalculationResponse result = engine.calculate(target(0, 0, 0, 0, 0, 0), 1.0, 1.0, null);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.ionBalance().deviationPercent()).isEqualTo(0.0);
        for (NutrientTankAllocationResponse t : result.tanks()) {
            assertThat(t.items()).isEmpty();
        }
    }

    // ── 이온 밸런스 경고 임계값(30%, contract §4.9 2026-08-23 정정) ─────────────────

    @Test
    @DisplayName("TOMATO 프리셋 4단계는 전부 이온밸런스 경고가 뜨지 않는다(정상 프리셋에서 alarm fatigue 방지)")
    void normalPresetsNeverTriggerIonBalanceWarning() {
        List<NutrientTargetRequest> presets = List.of(
                target(90, 47, 144, 160, 60, 79),   // SEEDLING, 25.33%
                target(120, 47, 210, 169, 60, 79),  // VEGETATIVE, 22.09%
                target(165, 47, 342, 169, 60, 79),  // FRUITING, 19.30%
                target(190, 47, 350, 200, 60, 79)   // HARVEST, 17.59%
        );

        for (NutrientTargetRequest preset : presets) {
            NutrientCalculationResponse result = engine.calculate(preset, 1.0, 1.0, null);
            assertThat(result.ionBalance().deviationPercent()).isLessThan(30.0);
            assertThat(result.warnings()).noneSatisfy(w -> assertThat(w).contains("이온 밸런스"));
        }
    }

    @Test
    @DisplayName("이온 밸런스 편차가 30%를 초과하는 커스텀 목표는 경고가 뜨고, NO3-N 가정 한계도 문구에 밝힌다")
    void imbalancedCustomTargetTriggersIonBalanceWarning() {
        // Ca만 크고(양이온) N은 미미(음이온)해 편차가 30%를 크게 초과하도록 구성.
        NutrientCalculationResponse result = engine.calculate(target(1, 0, 0, 500, 0, 0), 1.0, 1.0, null);

        assertThat(result.ionBalance().deviationPercent()).isGreaterThan(30.0);
        assertThat(result.warnings()).anySatisfy(w -> {
            assertThat(w).contains("이온 밸런스");
            assertThat(w).contains("30");
            assertThat(w).contains("NO3-N");
        });
    }

    @Test
    @DisplayName("양이온·음이온이 균형 잡힌 목표는 편차 0%로 경고가 전혀 없다")
    void balancedTargetProducesNoWarnings() {
        // K(양이온)만 200ppm, N(음이온)만 71.65ppm — KNO3 하나로 양쪽이 정확히 같은 me/L이 되도록
        // 역산한 값(K/N 몰비 = KNO3 자체의 조성비와 같으므로 다른 비료 없이 완전히 균형 잡힌다).
        NutrientCalculationResponse result = engine.calculate(target(71.65, 0, 200, 0, 0, 0), 1.0, 1.0, null);

        assertThat(result.ionBalance().deviationPercent()).isCloseTo(0.0, offset(0.1));
        assertThat(result.warnings()).isEmpty();

        // Ca=0·Mg=0·P=0이라 질산칼슘 4수염·MKP·MgSO4는 아예 투입되지 않는다(0g 항목을 만들지 않음).
        NutrientTankAllocationResponse tankA = tank(result, "A");
        assertThat(tankA.items()).hasSize(1);
        assertThat(tankA.items().get(0).formula()).isEqualTo("KNO3");
        NutrientTankAllocationResponse tankB = tank(result, "B");
        assertThat(tankB.items()).isEmpty();
    }

    // ── 스케일(탱크 용량×농축배율) 불변성 ───────────────────────────────

    @Test
    @DisplayName("ppm 목표가 같으면 tankVolumeL·concentrationFactor가 달라도 이온밸런스·EC·경고는 동일하고 질량만 비례한다")
    void scalingIsLinearInMass() {
        NutrientTargetRequest t = target(90, 47, 144, 160, 60, 79);
        NutrientCalculationResponse base = engine.calculate(t, 1.0, 1.0, null);
        NutrientCalculationResponse scaled = engine.calculate(t, 50.0, 10.0, null); // scale 500배

        assertThat(scaled.ionBalance().deviationPercent()).isCloseTo(base.ionBalance().deviationPercent(),
                offset(0.01));
        assertThat(scaled.estimatedEc()).isCloseTo(base.estimatedEc(), offset(0.01));
        assertThat(scaled.warnings()).hasSameSizeAs(base.warnings());

        // base는 이미 소수점 2자리로 반올림된 값이라(0.94 등) 500배 확대 시 반올림 오차도 500배
        // 증폭된다(최대 0.005 × 500 = 2.5g) — 그래서 넉넉한 허용오차를 둔다.
        double baseCaNitrate = tank(base, "A").items().get(0).amountG();
        double scaledCaNitrate = tank(scaled, "A").items().get(0).amountG();
        assertThat(scaledCaNitrate).isCloseTo(baseCaNitrate * 500, offset(3.0));
    }

    // ── 탱크 침전 위험 검증 로직(A/B를 일부러 뒤섞은 케이스가 N003이 되는지 직접 고정) ──────────────

    @Test
    @DisplayName("같은 탱크에 Ca와 인산 성분을 뒤섞으면 N003(침전 위험)")
    void mixingCalciumAndPhosphateInSameTankThrowsN003() {
        Map<String, List<Fertilizer>> mixed = Map.of(
                "A", List.of(Fertilizer.CALCIUM_NITRATE_TETRAHYDRATE, Fertilizer.MONOPOTASSIUM_PHOSPHATE));

        assertThatThrownBy(() -> engine.validateNoPrecipitationRisk(mixed))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.N003));
    }

    @Test
    @DisplayName("같은 탱크에 Ca와 황산 성분을 뒤섞으면 N003(침전 위험)")
    void mixingCalciumAndSulfateInSameTankThrowsN003() {
        Map<String, List<Fertilizer>> mixed = Map.of(
                "B", List.of(Fertilizer.CALCIUM_NITRATE_TETRAHYDRATE, Fertilizer.MAGNESIUM_SULFATE_HEPTAHYDRATE));

        assertThatThrownBy(() -> engine.validateNoPrecipitationRisk(mixed))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.N003));
    }

    @Test
    @DisplayName("정상 배치(A=칼슘계, B=인산·황산계)는 침전 위험 검증을 통과한다")
    void properTankSeparationPassesValidation() {
        Map<String, List<Fertilizer>> proper = Map.of(
                "A", List.of(Fertilizer.CALCIUM_NITRATE_TETRAHYDRATE, Fertilizer.POTASSIUM_NITRATE),
                "B", List.of(Fertilizer.MONOPOTASSIUM_PHOSPHATE, Fertilizer.MAGNESIUM_SULFATE_HEPTAHYDRATE,
                        Fertilizer.POTASSIUM_SULFATE));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> engine.validateNoPrecipitationRisk(proper));
    }
}
