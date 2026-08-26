package com.smartfarm.service.repository;

import java.time.LocalDateTime;

/**
 * 홈 대시보드 카드 지표 3열(온도·습도·EC, 이슈 #139) — 여러 농장 × 여러 지표의 "농장 전체 최신값"을
 * 배치 조회한 결과. {@code findLatestPerLevel}과 동일한 2단계 구조(① farm×metric별 가장 최근 tick을
 * 찾고 ② 그 tick 안에서 device 간 평균)를 층 축 대신 농장 축에 적용한다.
 */
public interface ReadingFarmLatestProjection {

    Long getFarmId();

    String getMetric();

    Double getValue();

    LocalDateTime getMeasuredAt();
}
