package com.smartfarm.service.entity;

/**
 * 알람 규칙의 지표 데이터 소스(이슈 #118) — 평가 엔진의 라우팅 키다.
 *
 * <p>세 소스는 스코프 표현력이 다르다:
 * <ul>
 *   <li>{@link #ENV_SNAPSHOT} — V9 {@code env_snapshots}. ai-server의 단일 하우스 실측이라
 *       <b>farmId조차 없는 전역 단일 스트림</b>이다. 따라서 하위 스코프가 성립하지 않아 규칙의
 *       {@code scopeType}은 {@code FARM}만 허용한다(V20 CHECK 제약으로 DB에서도 강제).</li>
 *   <li>{@link #SENSOR_READING} — V15 {@code sensor_readings}. {@code zone_id}/{@code rack_id}/
 *       {@code rack_level_id}를 갖고 있어 존·랙·층 스코프가 그대로 성립한다.</li>
 *   <li>{@link #DEVICE_HEARTBEAT} — 장비 통신 두절. 값 비교가 아니라 부재 판정이라
 *       {@code comparator}는 {@link AlarmComparator#ABSENT}만, {@code metric}은 null이다.</li>
 * </ul>
 */
public enum AlarmRuleSource {
    ENV_SNAPSHOT,
    SENSOR_READING,
    DEVICE_HEARTBEAT
}
