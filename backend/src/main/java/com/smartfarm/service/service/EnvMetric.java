package com.smartfarm.service.service;

/** 임계치 평가 대상 항목(contract §4.6 — "농장×항목×방향별 쿨다운"의 항목). */
public enum EnvMetric {
    INDOOR_TEMP("실내 온도", "°C"),
    INDOOR_HUMIDITY("실내 습도", "%");

    private final String label;
    private final String unit;

    EnvMetric(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }
}
