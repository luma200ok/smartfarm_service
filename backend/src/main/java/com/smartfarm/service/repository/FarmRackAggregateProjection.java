package com.smartfarm.service.repository;

/**
 * 홈 대시보드 카드(이슈 #139) — 농장별 랙 수·총 층 수 배치 집계. {@code Rack.levelCount}는 랙
 * 생성·수정 시점에 이미 실제 층 수와 동기화되는 비정규화 컬럼이라({@link
 * com.smartfarm.service.service.RackService#updateRack} → {@code changeLevelCount}), rack_levels
 * 테이블을 조인하지 않고 racks만 집계해도 총 층 수가 정확하다 — 쿼리 하나로 랙 수·층 수를 동시에
 * 구해 farm 수와 무관하게 쿼리 1개로 끝낸다(N+1 방지).
 */
public interface FarmRackAggregateProjection {

    Long getFarmId();

    Long getRackCount();

    Long getLevelCount();
}
