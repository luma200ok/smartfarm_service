package com.smartfarm.service.repository;

/**
 * 장비×지표 직전 측정값(contract §4.12 시뮬레이터 연동) — 목표값 수렴은 "직전 값에서 목표까지의
 * 차이 × 비율"이라 tick마다 device·metric별 마지막 값이 필요하다.
 */
public interface ReadingLatestValueProjection {

    Long getDeviceId();

    String getMetric();

    Double getValue();
}
