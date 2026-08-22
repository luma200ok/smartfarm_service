package com.smartfarm.service.repository;

import java.time.LocalDateTime;

/**
 * {@link EnvSnapshotRepository#findAggregated} 다운샘플 집계 결과 프로젝션(contract §4.6: 7d=30분
 * 평균, 30d=2시간 평균). 네이티브 쿼리의 컬럼 별칭을 getter 프로퍼티명과 동일하게 큰따옴표로 고정해
 * (예: {@code AS "bucket"}) camelCase 변환 추측에 기대지 않는다.
 */
public interface EnvSnapshotBucketProjection {

    LocalDateTime getBucket();

    Double getOutdoorTemp();

    Double getOutdoorHumidity();

    Double getIndoorTemp();

    Double getIndoorHumidity();
}
