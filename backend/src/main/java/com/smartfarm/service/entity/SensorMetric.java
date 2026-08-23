package com.smartfarm.service.entity;

/**
 * 센서 측정 지표(contract §4.11, 이슈 #90) — 프리뷰 데이터 화면 항목 7종. {@link #unit()}은 서버가
 * 응답에 실어주는 표시 단위(EnvMetric 선례와 동일하게 enum에 붙인다).
 */
public enum SensorMetric {
    TEMPERATURE("°C"),
    HUMIDITY("%"),
    CO2("ppm"),
    EC("dS/m"),
    PH("pH"),
    PPFD("µmol/m²/s"),
    POWER("kW");

    private final String unit;

    SensorMetric(String unit) {
        this.unit = unit;
    }

    public String unit() {
        return unit;
    }
}
