package com.smartfarm.service.entity;

/**
 * 알람 규칙·이벤트의 평가 스코프(이슈 #118, contract §4.10 계층 모델) — 프리뷰의 위치 표기
 * ("군산1 · B3랙 4층")와 "같은 지표라도 랙이 다르면 독립 알람"을 성립시킨다.
 *
 * <p>{@code ReadingScope.Type}(§4.11 조회 파라미터 파싱)과 값 집합이 같지만 별개 타입으로 둔다 —
 * 그쪽은 쿼리 파라미터 형식 해석용 package-private record이고, 이쪽은 영속 컬럼이다.
 */
public enum AlarmScopeType {
    FARM,
    ZONE,
    RACK,
    LEVEL
}
