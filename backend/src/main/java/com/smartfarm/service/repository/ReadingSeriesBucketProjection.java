package com.smartfarm.service.repository;

import java.time.LocalDateTime;

/**
 * {@link SensorReadingRepository#findSeriesAggregated} 다운샘플 집계 결과(contract §4.11) —
 * EnvSnapshotBucketProjection과 동일하게 네이티브 쿼리 컬럼 별칭을 getter 프로퍼티명과 동일하게
 * 큰따옴표로 고정한다.
 */
public interface ReadingSeriesBucketProjection {

    LocalDateTime getBucket();

    Double getValue();
}
