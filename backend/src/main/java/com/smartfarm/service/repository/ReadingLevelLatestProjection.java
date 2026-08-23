package com.smartfarm.service.repository;

import java.time.LocalDateTime;

/** {@link SensorReadingRepository#findLatestPerLevel} 랙 도면 셀(최신값) 집계 결과(contract §4.11). */
public interface ReadingLevelLatestProjection {

    Long getRackLevelId();

    Double getValue();

    LocalDateTime getMeasuredAt();
}
