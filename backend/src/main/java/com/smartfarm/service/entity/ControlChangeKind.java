package com.smartfarm.service.entity;

/**
 * 적용 대기 큐 항목의 종류(contract §4.12) — 목표값 변경({@code SETPOINT})과 장비 직접 토글
 * ({@code DEVICE}). 종류에 따라 필수 필드가 다르다(SETPOINT=metric·targetValue,
 * DEVICE=deviceId·targetStatus) — 검증은 {@code ControlService}가 수행한다.
 */
public enum ControlChangeKind {
    SETPOINT,
    DEVICE
}
