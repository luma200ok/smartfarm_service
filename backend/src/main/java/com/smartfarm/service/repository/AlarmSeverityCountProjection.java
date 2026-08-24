package com.smartfarm.service.repository;

/**
 * {@link AlarmEventRepository#countBySeverityAfter} 집계 결과 프로젝션(이슈 #116 리뷰 P2-C).
 * severity는 DB 컬럼(VARCHAR) 그대로 문자열로 받는다 — 네이티브 쿼리의 enum 자동 변환에 기대지
 * 않고 서비스 계층에서 {@code AlarmSeverity.valueOf(...)}로 명시 변환한다
 * (EnvSnapshotBucketProjection 선례와 동일하게 네이티브 쿼리 별칭을 getter 프로퍼티명과 동일하게
 * 고정 — camelCase 변환 추측에 기대지 않음).
 */
public interface AlarmSeverityCountProjection {

    String getSeverity();

    Long getEventCount();
}
