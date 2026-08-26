package com.smartfarm.service.repository;

import java.time.LocalDate;

/**
 * 홈 대시보드 카드 7일 미니 추이(이슈 #139) — 여러 농장의 대표 지표(TEMPERATURE) 일별 평균을 배치
 * 조회한 결과({@code findSeriesAggregated}와 같은 device-평균→시간-평균 2단계를 버킷=1일로 적용).
 */
public interface ReadingFarmDailyTrendProjection {

    Long getFarmId();

    LocalDate getBucketDate();

    Double getValue();
}
