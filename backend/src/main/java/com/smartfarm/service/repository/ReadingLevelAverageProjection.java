package com.smartfarm.service.repository;

/** {@link SensorReadingRepository#findLevelSummaryAggregated} 층별×지표별 평균(contract §4.11). */
public interface ReadingLevelAverageProjection {

    Long getRackLevelId();

    String getMetric();

    Double getAverage();
}
