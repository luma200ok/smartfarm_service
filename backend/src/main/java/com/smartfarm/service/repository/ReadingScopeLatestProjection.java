package com.smartfarm.service.repository;

import java.time.LocalDateTime;

/**
 * 스코프(농장/존/랙/층) 단위 최신 측정값 1건(이슈 #118 알람 규칙 평가) —
 * {@link SensorReadingRepository#findLatestInScope} 전용 네이티브 projection.
 */
public interface ReadingScopeLatestProjection {

    Double getValue();

    LocalDateTime getMeasuredAt();
}
