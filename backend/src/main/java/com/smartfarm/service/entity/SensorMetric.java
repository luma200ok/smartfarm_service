package com.smartfarm.service.entity;

/**
 * 센서 측정 지표(contract §4.11, 이슈 #90) — 프리뷰 데이터 화면 항목 7종. {@link #unit()}은 서버가
 * 응답에 실어주는 표시 단위(EnvMetric 선례와 동일하게 enum에 붙인다).
 */
public enum SensorMetric {
    TEMPERATURE("온도", "°C"),
    HUMIDITY("습도", "%"),
    CO2("CO2", "ppm"),
    EC("EC", "dS/m"),
    PH("pH", "pH"),
    PPFD("PPFD", "µmol/m²/s"),
    POWER("전력", "kW");

    private final String label;
    private final String unit;

    SensorMetric(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    /**
     * 표시용 한국어 라벨(이슈 #135 — {@code AlarmRule} 한 줄 요약 조립용, {@code EnvMetric#label()}
     * 선례와 동일 패턴). FE {@code SENSOR_METRIC_LABELS}와 값이 동일해야 한다.
     */
    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    /**
     * 제어 가능한 지표인가(contract §4.12 — 프리뷰 목표값 4종). EC/PH/POWER는 제어 대상이 아니다
     * (EC/PH는 양액 배합 §4.9의 영역, POWER는 결과 지표라 목표값 개념이 성립하지 않는다).
     * {@code ControlSetpoint}의 metric은 이 조건을 만족해야 한다(위반 시 C001).
     */
    public boolean isControllable() {
        return this == TEMPERATURE || this == HUMIDITY || this == CO2 || this == PPFD;
    }
}
