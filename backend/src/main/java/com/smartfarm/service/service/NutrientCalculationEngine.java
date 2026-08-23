package com.smartfarm.service.service;

import com.smartfarm.service.dto.NutrientCalculationResponse;
import com.smartfarm.service.dto.NutrientIonBalanceResponse;
import com.smartfarm.service.dto.NutrientSourceWaterRequest;
import com.smartfarm.service.dto.NutrientTankAllocationResponse;
import com.smartfarm.service.dto.NutrientTankItemResponse;
import com.smartfarm.service.dto.NutrientTargetRequest;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 양액 배합 계산 엔진(contract §4.9, 이슈 #64) — "비료 투입량은 틀리면 실제 작물이 죽는 계산"이라
 * 모든 산출 과정을 주석에 근거와 함께 남긴다. 상태 없는 순수 계산기(리포지토리 의존 없음)라
 * calculate(미리보기)와 저장(create/update) 양쪽에서 재사용한다.
 *
 * <h2>알고리즘 개요(A/B 2탱크, 5개 비료염)</h2>
 * <ol>
 *   <li><b>Ca → A탱크, 질산칼슘 4수염만 사용.</b> Ca 목표(원수 보정 후)를 정확히 채우도록 질량을
 *       산정한다. 이 염은 Ca와 함께 NO3-N도 딸려온다(분자식상 불가피).</li>
 *   <li><b>남는 N → A탱크, 질산칼륨.</b> N 목표에서 위 1)이 이미 공급한 N을 뺀 나머지가 양수면
 *       질산칼륨으로 정확히 채운다. 이미 초과했으면(Ca 목표가 N 목표 대비 상대적으로 높은 조합)
 *       추가하지 않는다 — N이 목표보다 많이 공급되며, 이는 {@link #addDeliveryDeviationWarning}
 *       경고로 드러난다(질산칼슘 4수염이 유일한 Ca 공급원이라는 handoff 제약의 구조적 결과이지
 *       계산 버그가 아니다).</li>
 *   <li><b>P → B탱크, 제1인산칼륨(MKP)만 사용.</b> P 목표를 정확히 채운다. K가 딸려온다.</li>
 *   <li><b>Mg → B탱크, 황산마그네슘 7수염만 사용.</b> Mg 목표(원수 보정 후)를 정확히 채운다. S가
 *       딸려온다.</li>
 *   <li><b>남는 K → B탱크, 황산칼륨.</b> K 목표에서 3)·2)가 공급한 K를 뺀 나머지가 양수면 황산칼륨
 *       으로 정확히 채운다(부산물로 S가 추가된다).</li>
 * </ol>
 *
 * <p>Ca·P·Mg·K는(질산칼륨이 필요한 경우 포함) 항상 목표치를 정확히 맞춘다. N과 S만 조합 특성상
 * 목표에서 벗어날 수 있고, 10% 초과 시 경고로 알린다(N003을 던지지 않는다 — 계약상 N003은
 * "산출 투입량이 음수"·"탱크 침전 위반"만 대상).
 *
 * <h2>이온 밸런스·EC는 "목표(target)" 기준</h2>
 * <p>투입량(tanks)은 전부 전기적으로 중성인 염(예: Ca(NO3)2, KNO3)만 조합해 만들기 때문에, 실제
 * 투입 결과 기준으로 양이온·음이온 me/L 합을 계산하면 항상 거의 정확히 일치한다(전하 보존 —
 * 중성 염을 더하는 한 이는 수학적으로 항상 성립하며, 어떤 target을 넣어도 마찬가지다). 그래서
 * "투입 결과 기준" 이온 밸런스는 정보성이 없다 — 항상 0%에 수렴해 계약이 요구하는 "편차 초과 시
 * 경고"가 실질적으로 절대 발동하지 못한다. 대신 이온 밸런스·EC는 <b>목표(target, 원수 보정 후)
 * ppm 자체</b>가 화학적으로 이온 밸런스가 맞는 조합인지를 검증하는 데 쓴다 — 이쪽이 실제로
 * "이 목표 처방 자체가 무리한 조합은 아닌가"를 드러내는 유의미한 신호다. 임계값은 30%다
 * ({@link #ION_BALANCE_WARNING_THRESHOLD_PERCENT} 주석 참고 — contract §4.9 2026-08-23 정정).
 */
@Component
public class NutrientCalculationEngine {

    // 원자량(IUPAC 표준원자량, g/mol) — Fertilizer 상수와 동일 출처.
    private static final double CA_ATOMIC_WEIGHT = 40.08;
    private static final double MG_ATOMIC_WEIGHT = 24.305;
    private static final double K_ATOMIC_WEIGHT = 39.098;
    private static final double N_ATOMIC_WEIGHT = 14.007;
    private static final double P_ATOMIC_WEIGHT = 30.974;
    private static final double S_ATOMIC_WEIGHT = 32.06;

    // 이온가(valence) — 원예 pH 범위(5.5~6.5)에서 우세한 이온 형태 기준: NO3-(1가), H2PO4-(1가),
    // SO4^2-(2가), Ca2+(2가), Mg2+(2가), K+(1가).
    private static final double CA_VALENCE = 2;
    private static final double MG_VALENCE = 2;
    private static final double K_VALENCE = 1;
    private static final double N_VALENCE = 1;
    private static final double P_VALENCE = 1;
    private static final double S_VALENCE = 2;

    /** 목표 대비 실제 투입 결과(N/K/S 부산물) 편차 경고 임계값 — 계약 §4.9 그대로 10%. */
    private static final double DEVIATION_WARNING_THRESHOLD_PERCENT = 10.0;

    /**
     * 이온 밸런스 편차 경고 임계값 — contract §4.9 2026-08-23 정정으로 10%→30%. N을 전량 NO3-N으로
     * 가정(NH4-N 미반영, {@link NutrientPresets} 클래스 주석 "가정" 참고)해 편차가 구조적으로 과대
     * 산출되고, TOMATO 프리셋 4단계 실측 편차가 17.59~25.33%로 전부 10%를 넘어 정상 프리셋에서도
     * 경고가 상시 발동하는 alarm fatigue가 있었다. 참조 프리셋 최댓값(25.33%)보다 위로 잡아 "정상
     * 범위를 넘어선 목표"만 걸리도록 30%로 재조정했다 — 임의 조정이 아니라 실측값 기반 재보정이다.
     */
    private static final double ION_BALANCE_WARNING_THRESHOLD_PERCENT = 30.0;

    private record TankItemComputation(Fertilizer fertilizer, double massG) {
    }

    public NutrientCalculationResponse calculate(NutrientTargetRequest target, double tankVolumeL,
                                                   double concentrationFactor, NutrientSourceWaterRequest sourceWater) {
        double sourceCa = sourceWater != null && sourceWater.ca() != null ? sourceWater.ca() : 0.0;
        double sourceMg = sourceWater != null && sourceWater.mg() != null ? sourceWater.mg() : 0.0;

        double caEff = target.ca() - sourceCa;
        double mgEff = target.mg() - sourceMg;

        if (caEff < 0 || mgEff < 0) {
            List<String> excess = new ArrayList<>();
            if (caEff < 0) {
                excess.add("Ca");
            }
            if (mgEff < 0) {
                excess.add("Mg");
            }
            throw new CustomException(ErrorCode.N003,
                    ErrorCode.N003.getMessage() + " 원수 보정 과다로 투입량이 음수입니다(초과 성분: "
                            + String.join(", ", excess) + ").");
        }

        // ppm(mg/L) → 탱크(농축 원액)에 실제로 넣을 총 질량(g) 환산 배율.
        // g = ppm(mg/L) × concentrationFactor × tankVolumeL(L) / 1000(mg→g)
        double scale = concentrationFactor * tankVolumeL / 1000.0;

        Map<String, List<TankItemComputation>> tankItems = new LinkedHashMap<>();
        tankItems.put("A", new ArrayList<>());
        tankItems.put("B", new ArrayList<>());

        // 1) Ca → A탱크, 질산칼슘 4수염(반올림해서 0.00g으로 표시될 항목은 넣지 않는다 — 계산
        // 자체는 caEff=0이면 자연히 0이 되고, 극소 잔여량이 반올림 경계에서 남아도 "0.00g 투입"
        // 이라는 무의미한 표시 항목으로 결과를 지저분하게 만들지 않기 위한 표시상 처리일 뿐이다).
        Fertilizer caNitrate = Fertilizer.CALCIUM_NITRATE_TETRAHYDRATE;
        double caNitrateMassG = (caEff * scale) / caNitrate.getCaFraction();
        double nFromCaNitrate = caNitrateMassG * caNitrate.getNFraction();
        addTankItemIfDisplayable(tankItems.get("A"), caNitrate, caNitrateMassG);

        // 2) 남는 N → A탱크, 질산칼륨
        Fertilizer potassiumNitrate = Fertilizer.POTASSIUM_NITRATE;
        double nTargetG = target.n() * scale;
        double nRemainingG = nTargetG - nFromCaNitrate;
        double kFromKno3 = 0.0;
        double nDeliveredG = nFromCaNitrate;
        if (nRemainingG > 0) {
            double knO3MassG = nRemainingG / potassiumNitrate.getNFraction();
            kFromKno3 = knO3MassG * potassiumNitrate.getKFraction();
            nDeliveredG += nRemainingG;
            addTankItemIfDisplayable(tankItems.get("A"), potassiumNitrate, knO3MassG);
        }

        // 3) P → B탱크, 제1인산칼륨(MKP)
        Fertilizer mkp = Fertilizer.MONOPOTASSIUM_PHOSPHATE;
        double pTargetG = target.p() * scale;
        double mkpMassG = pTargetG / mkp.getPFraction();
        double kFromMkp = mkpMassG * mkp.getKFraction();
        addTankItemIfDisplayable(tankItems.get("B"), mkp, mkpMassG);

        // 4) Mg → B탱크, 황산마그네슘 7수염
        Fertilizer mgso4 = Fertilizer.MAGNESIUM_SULFATE_HEPTAHYDRATE;
        double mgTargetG = mgEff * scale;
        double mgso4MassG = mgTargetG / mgso4.getMgFraction();
        double sFromMgso4 = mgso4MassG * mgso4.getSFraction();
        addTankItemIfDisplayable(tankItems.get("B"), mgso4, mgso4MassG);

        // 5) 남는 K → B탱크, 황산칼륨
        Fertilizer k2so4 = Fertilizer.POTASSIUM_SULFATE;
        double kTargetG = target.k() * scale;
        double kDeliveredSoFarG = kFromMkp + kFromKno3;
        double kRemainingG = kTargetG - kDeliveredSoFarG;
        double sFromK2so4 = 0.0;
        double kDeliveredG = kDeliveredSoFarG;
        if (kRemainingG > 0) {
            double k2so4MassG = kRemainingG / k2so4.getKFraction();
            sFromK2so4 = k2so4MassG * k2so4.getSFraction();
            kDeliveredG += kRemainingG;
            addTankItemIfDisplayable(tankItems.get("B"), k2so4, k2so4MassG);
        }

        double sDeliveredG = sFromMgso4 + sFromK2so4;

        // 침전 위험 검증(실행되는 로직 — 방어적 재확인). 위 배치는 Ca 함유 염을 A탱크에만, PO4/SO4
        // 함유 염을 B탱크에만 두도록 설계돼 있어 이 시점엔 항상 통과하지만, 회귀 방지를 위해 실제
        // 배치 결과로 다시 검증한다.
        Map<String, List<Fertilizer>> tankFertilizers = new LinkedHashMap<>();
        tankItems.forEach((tank, items) -> tankFertilizers.put(tank,
                items.stream().map(TankItemComputation::fertilizer).toList()));
        validateNoPrecipitationRisk(tankFertilizers);

        List<String> warnings = new ArrayList<>();
        addDeliveryDeviationWarning(warnings, "N", target.n(), fromGrams(nDeliveredG, scale));
        addDeliveryDeviationWarning(warnings, "K", target.k(), fromGrams(kDeliveredG, scale));
        addDeliveryDeviationWarning(warnings, "S", target.s(), fromGrams(sDeliveredG, scale));

        // 이온 밸런스·EC — 목표(target, 원수 보정 후) ppm 기준(클래스 주석 "이온 밸런스·EC는 목표
        // 기준" 참고).
        double cationMeL = CA_VALENCE * caEff / CA_ATOMIC_WEIGHT
                + MG_VALENCE * mgEff / MG_ATOMIC_WEIGHT
                + K_VALENCE * target.k() / K_ATOMIC_WEIGHT;
        double anionMeL = N_VALENCE * target.n() / N_ATOMIC_WEIGHT
                + P_VALENCE * target.p() / P_ATOMIC_WEIGHT
                + S_VALENCE * target.s() / S_ATOMIC_WEIGHT;
        double deviationPercent = (cationMeL + anionMeL) == 0
                ? 0.0
                : Math.abs(cationMeL - anionMeL) / ((cationMeL + anionMeL) / 2) * 100.0;
        if (deviationPercent > ION_BALANCE_WARNING_THRESHOLD_PERCENT) {
            warnings.add(String.format(
                    "이온 밸런스 편차가 %.1f%%로 %.0f%%를 초과합니다(양이온 %.2f me/L, 음이온 %.2f me/L). "
                            + "이 계산은 N을 전량 NO3-N으로 가정(NH4-N 미반영)해 편차가 실제보다 과대"
                            + " 산출될 수 있으니 참고값으로만 보고 현장 EC·pH로 최종 확인하세요.",
                    deviationPercent, ION_BALANCE_WARNING_THRESHOLD_PERCENT, cationMeL, anionMeL));
        }

        // EC(dS/m) ≈ Σ(me/L)/10(contract §4.9 근사식). 전기적 중성 조건에서 양이온 합≈음이온 합이라
        // 원예 문헌 관용 표기대로 양이온 총 me/L 하나만 사용한다(두 쪽을 합하면 실측 EC 대비
        // 과대추정됨 — 이미 거의 같은 값을 두 번 세는 셈이라).
        double estimatedEc = cationMeL / 10.0;

        List<NutrientTankAllocationResponse> tanks = tankItems.entrySet().stream()
                .map(entry -> new NutrientTankAllocationResponse(entry.getKey(),
                        entry.getValue().stream()
                                .map(item -> new NutrientTankItemResponse(item.fertilizer().getDisplayName(),
                                        item.fertilizer().getFormula(), round2(item.massG())))
                                .toList()))
                .toList();

        return new NutrientCalculationResponse(tanks, round2(estimatedEc),
                new NutrientIonBalanceResponse(round2(cationMeL), round2(anionMeL), round2(deviationPercent)),
                warnings);
    }

    /**
     * 탱크 침전 위험 검증 — 같은 탱크에 Ca 함유 비료와 (PO4 또는 SO4) 함유 비료가 동시에 있으면
     * 인산칼슘·황산칼슘 침전 위험이라 N003을 던진다(contract §4.9 "코드 상수가 아니라 검증 로직으로
     * 강제"). {@link #calculate}가 실제 배치 결과로 방어적으로 호출하는 것과 별개로, 이 메서드는
     * public이라 테스트가 일부러 뒤섞은 입력을 직접 넣어 검증 로직 자체를 고정할 수 있다.
     */
    public void validateNoPrecipitationRisk(Map<String, List<Fertilizer>> tankContents) {
        for (Map.Entry<String, List<Fertilizer>> entry : tankContents.entrySet()) {
            boolean hasCalcium = entry.getValue().stream().anyMatch(Fertilizer::isProvidesCalcium);
            boolean hasPrecipitationRisk = entry.getValue().stream()
                    .anyMatch(f -> f.isProvidesPhosphate() || f.isProvidesSulfate());
            if (hasCalcium && hasPrecipitationRisk) {
                throw new CustomException(ErrorCode.N003,
                        ErrorCode.N003.getMessage() + " 같은 탱크(" + entry.getKey()
                                + ")에 칼슘과 인산/황산 성분을 동시에 배치할 수 없습니다(침전 위험).");
            }
        }
    }

    /**
     * 목표 대비 실제 투입 결과(ppm 환산)가 10%를 초과해 벗어나면 경고를 추가한다. target이 0이면
     * 비율(%) 계산이 무의미해(0으로 나누기) 건너뛰던 것을, target=0인데도 다른 성분을 채우는
     * 비료염의 부산물로 실제로는 0이 아닌 양이 공급되는 경우(예: N 목표 0인데 Ca(NO3)2가 Ca를
     * 채우며 N을 딸려 보내는 경우)를 놓치지 않도록 절대 ppm 기준 경고로 보완한다.
     */
    private void addDeliveryDeviationWarning(List<String> warnings, String element, double targetPpm,
                                              double deliveredPpm) {
        if (targetPpm <= 0) {
            if (deliveredPpm > 0.01) {
                warnings.add(String.format(
                        "%s 목표는 0ppm이지만 다른 성분(Ca/P/Mg/K)을 채우는 비료염의 부산물로 실제 %.1fppm이"
                                + " 공급됩니다.",
                        element, deliveredPpm));
            }
            return;
        }
        double deviationPercent = Math.abs(deliveredPpm - targetPpm) / targetPpm * 100.0;
        if (deviationPercent > DEVIATION_WARNING_THRESHOLD_PERCENT) {
            warnings.add(String.format(
                    "%s 실제 공급량이 목표 대비 %.1f%% 차이납니다(목표 %.1fppm, 실제 %.1fppm) — "
                            + "다른 성분(Ca/K)을 채우는 비료염에 딸려오는 부산물이라 정확히 맞추기 어렵습니다.",
                    element, deviationPercent, targetPpm, deliveredPpm));
        }
    }

    /** 탱크 질량(g) → ppm(mg/L) 환산(scale 역연산) — scale은 항상 양수(tankVolumeL≥1, concentrationFactor≥1). */
    private double fromGrams(double massG, double scale) {
        return massG / scale;
    }

    /** 반올림 결과가 0.00g보다 큰 경우에만 탱크 항목으로 추가한다(0.00g 표시 항목 방지). */
    private void addTankItemIfDisplayable(List<TankItemComputation> items, Fertilizer fertilizer, double massG) {
        if (round2(massG) > 0) {
            items.add(new TankItemComputation(fertilizer, massG));
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
