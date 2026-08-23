package com.smartfarm.service.service;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.GrowthStage;
import java.util.EnumMap;
import java.util.Map;

/**
 * 작물×생육단계별 목표 배양액 농도(ppm=mg/L) 프리셋(contract §4.9, 이슈 #64). DB가 아닌 코드
 * 상수(버전 관리·출처 추적 목적 — contract 명시).
 *
 * <p><b>출처</b>: Kroggel, M., &amp; Kubota, C. (2018). <i>Hydroponic Nutrient Solution for
 * Optimized Greenhouse Tomato Production.</i> The Ohio State University Extension, Department
 * of Horticulture and Crop Science, Fact Sheet HYG-1437. Table 3의 "4단계(4-stage) 프로그램"
 * (Dr. Merle Jensen이 개발한 Jensen/UA-CEA 양액 처방 — 상업 재배자를 위한 세분화 버전)을
 * 그대로 인용한다. N 값은 원문 표에 "NO3-N"으로 명시된 값이다(NH4-N 성분은 원문 표에 없어
 * 전량 NO3-N으로 가정 — 아래 "가정" 항목 참고).
 *
 * <p>원문 표 수치(mg/L, S1~S4 순서): N(NO3-N) 90/120/165/190, P 47/47/47/47,
 * K 144/210/342/350, Ca 160/169/169/200, Mg 60/60/60/60.
 *
 * <p><b>생육단계 매핑(설계 결정 — 계약에 미지정, 리뷰 필요)</b>: 원문 표의 S1~S4는 화방(truss)
 * 개화 시점 기준(자엽 출현~2화방 개화 / 3~5화방 개화 / 5화방 개화 이후 / Jensen 4단계 확장의
 * 최종 단계)이며, 우리 도메인 enum {@link GrowthStage}(SEEDLING/VEGETATIVE/FRUITING/HARVEST)와
 * 문헌상 1:1로 정의가 일치하지 않는다. S1→SEEDLING, S2→VEGETATIVE, S3→FRUITING, S4→HARVEST로
 * 발생 순서대로 단순 매핑했다 — 사람이 검토해야 할 설계 결정이라 handoff 완료 보고에 별도 명시한다.
 *
 * <p><b>S(황) 값 산출 방식</b>: 원문 표는 S를 "10~200 mg/L 범위"로만 제시하고 단계별 고정값을
 * 주지 않는다(범위 안에서 값을 임의로 골라 커밋하는 것은 "출처 없는 추정값" 금지 원칙에 위배된다고
 * 판단). 대신 handoff가 지정한 설계 — Mg 공급원이 전량 황산마그네슘(MgSO4·7H2O) 단일 염 —
 * 를 이용해, Mg 목표량이 그 염을 통해 자연히 공급하는 S량을 화학양론적으로 역산했다:
 * S = Mg × (S 원자량 / Mg 원자량) = Mg × (32.06 / 24.305) ≈ Mg × 1.31908.
 * Mg가 전 단계 60mg/L로 동일하므로 S도 4단계 모두 79mg/L(반올림, 정확히는 79.14…)로 동일하다.
 * 이는 추정치가 아니라 이미 확정된 두 사실(Mg 목표치·MgSO4 단일 공급원 설계)로부터 산출한 값이다
 * — {@link Fertilizer#MAGNESIUM_SULFATE_HEPTAHYDRATE}의 sFraction/mgFraction과 동일 근거.
 *
 * <p><b>가정</b>: N을 전량 NO3-N으로 취급(NH4-N 미포함). 실제 Jensen/야마자키 계열 처방은 통상
 * 소량의 NH4-N을 포함해 이온 밸런스를 맞추나, 원문 표가 NH4 분율을 제공하지 않아 반영하지
 * 못했다. 이 가정 때문에 아래 계산 엔진의 이온 밸런스 경고가 이 프리셋 4단계 전부에서 상시
 * 발생한다(위험이 아니라 프리셋 설계상 알려진 한계 — handoff 보고 참고).
 */
public final class NutrientPresets {

    private NutrientPresets() {
    }

    /** 목표 ppm(mg/L) 6성분. */
    public record Target(double n, double p, double k, double ca, double mg, double s) {
    }

    private static final Map<GrowthStage, Target> TOMATO = new EnumMap<>(GrowthStage.class);

    static {
        TOMATO.put(GrowthStage.SEEDLING, new Target(90, 47, 144, 160, 60, 79));
        TOMATO.put(GrowthStage.VEGETATIVE, new Target(120, 47, 210, 169, 60, 79));
        TOMATO.put(GrowthStage.FRUITING, new Target(165, 47, 342, 169, 60, 79));
        TOMATO.put(GrowthStage.HARVEST, new Target(190, 47, 350, 200, 60, 79));
    }

    private static final Map<CropType, Map<GrowthStage, Target>> PRESETS = Map.of(CropType.TOMATO, TOMATO);

    /** 없으면 null(작물 자체가 프리셋 미보유이거나 그 작물의 해당 단계가 없는 경우). */
    public static Target find(CropType cropType, GrowthStage stage) {
        Map<GrowthStage, Target> byStage = PRESETS.get(cropType);
        return byStage == null ? null : byStage.get(stage);
    }

    /** 프리셋이 없는 cropType이면 빈 맵(호출자는 빈 배열 응답으로 처리). */
    public static Map<GrowthStage, Target> byCropType(CropType cropType) {
        return PRESETS.getOrDefault(cropType, Map.of());
    }
}
