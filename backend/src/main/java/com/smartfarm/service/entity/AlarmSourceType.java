package com.smartfarm.service.entity;

/**
 * 알람 발생 소스(이슈 #116, #118로 확장) — {@link AlarmRule#alarmSourceType()}이 규칙의
 * {@link AlarmRuleSource}를 이 값으로 매핑한다.
 *
 * <p>{@link #ENV_THRESHOLD}는 이름을 그대로 둔다 — #116부터 쌓인 기존 이벤트 행이 이 문자열을
 * 갖고 있어(@Enumerated(STRING)) 이름을 바꾸면 과거 이력을 읽을 때 valueOf가 깨진다.
 */
public enum AlarmSourceType {
    /** V9 env_snapshots 기반 환경 임계치(§4.6) — #116 이전부터 쓰던 값. */
    ENV_THRESHOLD,
    /** V15 sensor_readings 기반 센서 임계치(§4.11 지표 7종, #118). */
    SENSOR_THRESHOLD,
    /** 장비 통신 두절(#118). */
    DEVICE_HEARTBEAT
}
