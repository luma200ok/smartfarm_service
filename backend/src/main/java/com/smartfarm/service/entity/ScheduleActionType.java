package com.smartfarm.service.entity;

/**
 * 스케줄 액션 종류(V25, 이슈 #129-C) — {@code Schedule#actionPayload}(JSONB)의 형태를 결정하는
 * 태그다. 이 골격 단계에서는 실행 로직이 없으므로 payload 형태를 애플리케이션이 강제 검증하지
 * 않는다(스케줄러가 붙는 후속 이슈에서 액션별 payload 스키마를 확정한다).
 */
public enum ScheduleActionType {
    DEVICE_ON,
    DEVICE_OFF,
    SETPOINT_CHANGE
}
