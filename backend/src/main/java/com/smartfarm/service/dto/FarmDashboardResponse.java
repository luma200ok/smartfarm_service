package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import java.time.LocalDate;
import java.util.List;

/**
 * 홈 대시보드 농장 카드 1건(이슈 #139, 시안 `01-dashboard-home` 4열 카드 선행) —
 * {@code GET /api/dashboard/farms}. 내 농장 <b>전체</b>를 배치 조회로 한 번에 반환해, 시안이
 * 요구하는 "농장 N개 × 3~4회 호출" N+1을 피한다({@code DashboardService} 참고).
 *
 * <p>⚠️ <b>{@code plantedDaysAgo}(정식 경과일) 필드가 없다</b> — 재배 사이클 도메인(#130)이 아직
 * 없어 계산할 방법이 없다. 0이나 임의값을 내보내면 거짓 정보가 되어 사용자의 생육 판단을 그르친다
 * (#130 완료 후 추가). 같은 원칙의 선례: #128 농약 출처 표기, #129 harvestDueSoon 미생성, #136
 * 추정 원인 미렌더.
 *
 * <p>{@code latestAlarmMessage}는 알람이 없으면 {@code null}이다 — "이상 없음" 같은 문구를 서버가
 * 지어내지 않는다(표기는 FE 판단).
 */
public record FarmDashboardResponse(
        Long id,
        String name,
        CropType cropType,
        int rackCount,
        int levelCount,
        FarmDashboardStatus status,
        long unacknowledgedAlarmCount,
        List<MetricValue> metrics,
        List<TrendPoint> trend7d,
        String latestAlarmMessage
) {

    /**
     * 카드 지표 3열(온도·습도·EC, 시안 고정) 1건. {@code value}는 신선도 상한(tick 주기 × 5,
     * {@code ReadingService}와 동일 기준)을 넘긴 값이거나 측정 이력이 아예 없으면 {@code null}이다
     * — 이때 {@code outOfRange}는 항상 {@code false}("범위 밖"을 판정할 값 자체가 없으므로).
     */
    public record MetricValue(String metric, String unit, Double value, boolean outOfRange) {
    }

    /**
     * 7일 미니 추이 1포인트(대표 지표=TEMPERATURE, 일별 평균). 그날 측정 이력이 없으면
     * {@code value=null}, {@code state="IDLE"}이다({@code ReadingMatrixResponse.LevelCell}과
     * 동일한 state 어휘 — OK/WARNING/CRITICAL/IDLE).
     */
    public record TrendPoint(LocalDate date, Double value, String state) {
    }
}
