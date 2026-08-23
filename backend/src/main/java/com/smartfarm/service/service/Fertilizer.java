package com.smartfarm.service.service;

import lombok.Getter;

/**
 * 양액 배합에 사용하는 비료염의 화학 조성 상수(이슈 #64, handoff 지정 비료 목록).
 *
 * <p><b>분자량 산출 근거</b>: 각 염의 분자량은 IUPAC 표준원자량(2021 conventional atomic weights)
 * 으로부터 화학양론적으로 계산한 값이다 — Ca 40.08, N 14.007, O 15.999, H 1.008, K 39.098,
 * P 30.974, Mg 24.305, S 32.06 (g/mol). 임의로 창작한 수치가 아니라 분자식 그대로의 산술이므로,
 * 분자식이 맞으면 이 값도 맞다. 각 상수 옆 주석에 계산 과정을 그대로 남긴다.
 *
 * <p><b>탱크 배치</b>(handoff): A탱크=칼슘계(Ca 함유 비료만), B탱크=인산·황산계(PO4/SO4 함유
 * 비료만). 같은 탱크에 Ca와 (PO4 또는 SO4)를 동시에 두면 인산칼슘·황산칼슘 침전이 생기므로 절대
 * 금지 — 이 규칙은 {@link NutrientCalculationEngine#validateNoPrecipitationRisk}가 실행 시점에
 * 강제한다(이 enum의 {@code providesCalcium}/{@code providesPhosphate}/{@code providesSulfate}
 * 플래그가 그 판정 근거).
 *
 * <p><b>범위 제외</b>: 미량요소 혼합제(Fe·B·Mn·Zn)는 계약(target)에 대응 성분 필드가 없어(N/P/K/
 * Ca/Mg/S만 존재) 임의로 시비량을 정할 수 없으므로 이번 범위에서 제외한다. 질산암모늄(NH4NO3)도
 * v1 알고리즘(질산칼슘 4수염으로 채우지 못한 N 부족분은 질산칼륨만으로 충분히 커버 가능하도록
 * 설계)에서는 실제로 필요한 경우가 없어 정의하지 않았다 — 후속 이슈에서 확장 가능.
 */
@Getter
public enum Fertilizer {

    /**
     * 질산칼슘 4수염 Ca(NO3)2·4H2O — A탱크. Ca와 NO3-N을 동시 공급.
     * 분자량 = Ca(40.08) + 2×N(14.007) + 6×O(15.999) + 4×[2×H(1.008)+O(15.999)]
     *        = 40.08 + 28.014 + 95.994 + 72.06 = 236.148 g/mol
     */
    CALCIUM_NITRATE_TETRAHYDRATE("질산칼슘 4수염", "Ca(NO3)2·4H2O", 236.148,
            40.08 / 236.148, 0.0, 0.0, 28.014 / 236.148, 0.0, 0.0,
            true, false, false),

    /**
     * 질산칼륨 KNO3 — A탱크(질산칼슘 4수염만으로 못 채운 N 부족분을 K와 함께 보충). Ca·PO4·SO4를
     * 포함하지 않아 A탱크에 두어도 침전 규칙을 위반하지 않는다.
     * 분자량 = K(39.098) + N(14.007) + 3×O(15.999) = 39.098 + 14.007 + 47.997 = 101.102 g/mol
     */
    POTASSIUM_NITRATE("질산칼륨", "KNO3", 101.102,
            0.0, 39.098 / 101.102, 0.0, 14.007 / 101.102, 0.0, 0.0,
            false, false, false),

    /**
     * 제1인산칼륨(MKP) KH2PO4 — B탱크. P와 K를 동시 공급.
     * 분자량 = K(39.098) + 2×H(1.008) + P(30.974) + 4×O(15.999)
     *        = 39.098 + 2.016 + 30.974 + 63.996 = 136.084 g/mol
     */
    MONOPOTASSIUM_PHOSPHATE("제1인산칼륨(MKP)", "KH2PO4", 136.084,
            0.0, 39.098 / 136.084, 30.974 / 136.084, 0.0, 0.0, 0.0,
            false, true, false),

    /**
     * 황산마그네슘 7수염 MgSO4·7H2O — B탱크. Mg와 S(SO4)를 동시 공급.
     * 분자량 = Mg(24.305) + S(32.06) + 4×O(15.999) + 7×[2×H(1.008)+O(15.999)]
     *        = 24.305 + 32.06 + 63.996 + 126.105 = 246.466 g/mol
     */
    MAGNESIUM_SULFATE_HEPTAHYDRATE("황산마그네슘 7수염", "MgSO4·7H2O", 246.466,
            0.0, 0.0, 0.0, 0.0, 24.305 / 246.466, 32.06 / 246.466,
            false, false, true),

    /**
     * 황산칼륨 K2SO4 — B탱크(제1인산칼륨만으로 못 채운 K 부족분을 S와 함께 보충).
     * 분자량 = 2×K(39.098) + S(32.06) + 4×O(15.999) = 78.196 + 32.06 + 63.996 = 174.252 g/mol
     */
    POTASSIUM_SULFATE("황산칼륨", "K2SO4", 174.252,
            0.0, 78.196 / 174.252, 0.0, 0.0, 0.0, 32.06 / 174.252,
            false, false, true);

    private final String displayName;
    private final String formula;
    private final double molarMass;
    /** 이 염 1g당 공급되는 Ca 질량 비율(무함유면 0.0). */
    private final double caFraction;
    private final double kFraction;
    private final double pFraction;
    private final double nFraction;
    private final double mgFraction;
    private final double sFraction;
    private final boolean providesCalcium;
    private final boolean providesPhosphate;
    private final boolean providesSulfate;

    Fertilizer(String displayName, String formula, double molarMass, double caFraction, double kFraction,
               double pFraction, double nFraction, double mgFraction, double sFraction, boolean providesCalcium,
               boolean providesPhosphate, boolean providesSulfate) {
        this.displayName = displayName;
        this.formula = formula;
        this.molarMass = molarMass;
        this.caFraction = caFraction;
        this.kFraction = kFraction;
        this.pFraction = pFraction;
        this.nFraction = nFraction;
        this.mgFraction = mgFraction;
        this.sFraction = sFraction;
        this.providesCalcium = providesCalcium;
        this.providesPhosphate = providesPhosphate;
        this.providesSulfate = providesSulfate;
    }
}
