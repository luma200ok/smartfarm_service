package com.smartfarm.service.entity;

/**
 * 존 운전 모드(contract §4.12) — 프리뷰 "자동 운전" 토글. 모드에 따라 허용 조작이 갈린다:
 * {@code AUTO}는 목표값 편집만, {@code MANUAL}은 장비 직접 토글만 허용한다(위반 시 CT003).
 * 미설정 존은 {@code AUTO}로 간주한다(행 생성 전에도 조회가 성립해야 한다 — contract §4.12 모델).
 */
public enum OperationMode {

    AUTO,
    MANUAL;

    /** 이 모드에서 목표값(SETPOINT) 편집이 허용되는가 — contract §4.12 "운전 모드와 허용 조작" 표. */
    public boolean allowsSetpointChange() {
        return this == AUTO;
    }

    /** 이 모드에서 장비 직접 토글(DEVICE)이 허용되는가 — contract §4.12 "운전 모드와 허용 조작" 표. */
    public boolean allowsDeviceChange() {
        return this == MANUAL;
    }
}
