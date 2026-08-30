package com.smartfarm.service.dto;

import java.util.List;

/**
 * 홈 대시보드 농장 카드 목록 응답(이슈 #140) — {@code GET /api/dashboard/farms}.
 *
 * <p>내 활성 농장이 집계 상한({@code dashboard.max-farms}, 기본 50)을 넘으면 {@link DashboardService}
 * 가 초과분을 조용히 자른다. 응답이 평문 배열이던 시절에는 FE가 잘렸는지 알 방법이 없어 카드가 그냥
 * 사라진 것처럼 보였다 — 이 래퍼가 그 절단 여부를 명시적 신호로 실어준다.
 *
 * @param farms      실제로 집계해 반환한 농장 카드(절단 후 목록)
 * @param totalCount <b>절단 전</b> 내 활성 농장 수. 절단이 없으면 {@code farms.size()}와 같다
 * @param truncated  상한 초과로 잘렸는지 여부. 절단이 없으면 {@code false}
 */
public record DashboardFarmsResponse(
        List<FarmDashboardResponse> farms,
        int totalCount,
        boolean truncated
) {
}
