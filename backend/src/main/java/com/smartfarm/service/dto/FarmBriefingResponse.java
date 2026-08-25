package com.smartfarm.service.dto;

/**
 * 홈 화면 "오늘 할일" 브리핑(이슈 #129-B) — 기존 조회를 재사용해 합산하는 집계 단일 엔드포인트
 * ({@code AlarmEventService#unacknowledgedCount}·{@code DeviceService#summary}, 새 쿼리 없음).
 *
 * <p>⚠️ <b>필드는 전부 "건수"다</b> — 프리뷰의 "조치 필요 2곳"은 <b>농장 수</b> 단위지만, 이 API는
 * 로그인한 농장 하나를 기준으로 <b>이벤트/장비 건수</b>를 센다. 의미가 다르므로 필드명에 {@code Count}를
 * 붙여 명확히 한다.
 *
 * @param actionRequiredCount   미확인 알람 건수 ({@code AlarmEventService#unacknowledgedCount} 그대로).
 * @param calibrationDueSoonCount 보정 기한 임박 장비 수 ({@code DeviceService#summary}의
 *                              {@code calibrationDueSoon} 그대로).
 *                              <p>⚠️ <b>{@code harvestDueSoon}(수확 예정) 필드는 의도적으로 없다</b> —
 *                              랙별 재배 사이클(작물·정식일·예상 수확일) 도메인이 아직 없어 계산할 수
 *                              없다. 없는 값을 0으로 내보내면 "수확 예정 0건"이라는 거짓 정보가 되어
 *                              사용자가 수확 시기를 놓칠 수 있다 — 후속 이슈 #130으로 분리됐다.
 */
public record FarmBriefingResponse(
        long actionRequiredCount,
        long calibrationDueSoonCount
) {
}
