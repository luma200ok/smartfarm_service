package com.smartfarm.service.entity;

/**
 * 장비 통신·동작 상태(contract §4.10) — 프리뷰 statusTone ok/warning/critical + 통신두절(OFFLINE).
 *
 * <p>{@code OFF}는 사이클 3(제어 도메인, contract §4.12)에서 추가됐다 — §4.12가 장비 직접 토글의
 * 대상을 {@code Device.status}로 명시하고("Device.status == OFF인 제어기가 있는 존은 해당 지표를
 * 수렴시키지 않는다"), 비상 정지가 "전 존 장비 OFF"로 정의되므로 <b>의도적으로 꺼진 상태</b>를
 * 표현할 값이 필요하다. 통신두절({@code OFFLINE})과는 구분된다 — OFFLINE은 장애, OFF는 조작 결과다.
 *
 * <p>⚠️ 켜기 조작은 {@code NORMAL}로 되돌린다 — 끄기 전 상태가 WARNING/FAULT였더라도 그 값은
 * 복원되지 않는다(장비 상태는 다음 관측으로 갱신되는 값이고, 조작 이력 자체는
 * {@code control_changes}/{@code control_apply_logs}에 남는다).
 */
public enum DeviceStatus {
    NORMAL,
    WARNING,
    FAULT,
    OFFLINE,
    OFF
}
