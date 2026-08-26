package com.smartfarm.service.repository;

import com.smartfarm.service.entity.AlarmSeverity;

/**
 * 홈 대시보드 카드(이슈 #139) — 여러 농장의 미확인 알람을 severity별로 배치 집계한 결과(N+1 방지).
 * status 배지(CRITICAL/WARNING/NORMAL) 파생과 unacknowledgedAlarmCount 계산 둘 다 이 결과 하나로
 * 충분해 별도 카운트 쿼리를 두지 않는다. JPQL 프로젝션이라(네이티브 아님) severity를 문자열이 아닌
 * enum 타입 그대로 받는다({@link AlarmSeverityCountProjection}과 달리 ordinal 바인딩 문제가 없음).
 */
public interface AlarmSeverityFarmCountProjection {

    Long getFarmId();

    AlarmSeverity getSeverity();

    Long getEventCount();
}
