package com.smartfarm.service.repository;

/**
 * 홈 대시보드 카드 하단 한 줄 요약(이슈 #139) — 농장별 가장 최근 알람 이벤트(상태 무관, occurredAt
 * 최신)의 메시지를 배치 조회한 결과. Postgres {@code DISTINCT ON}으로 그룹별 최신 1행을 한 번에
 * 뽑는다(N+1 방지 — {@code SensorReadingRepository#findLatestValueByDeviceIds}와 동일 패턴).
 */
public interface FarmLatestAlarmProjection {

    Long getFarmId();

    String getMessage();
}
