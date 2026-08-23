package com.smartfarm.service.entity;

/**
 * 측정값 출처(contract §4.11) — 행 단위로 남긴다. 시뮬레이터를 끄고 실기기를 붙이면 한 테이블에
 * 두 출처가 섞이므로, 행마다 출처를 남기지 않으면 사후 구분이 불가능하다(handoff).
 */
public enum SensorSource {
    SIMULATED,
    DEVICE
}
