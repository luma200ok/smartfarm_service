package com.smartfarm.service.dto;

import java.util.List;

/**
 * {@code GET /readings/level-summary} 응답(contract §4.11) — "층별 평균 + 목표 대비 편차(데이터
 * 화면 비교표)"라고만 서술되고 <b>DTO 필드가 계약서에 명시되지 않은 항목</b>이다. 엔드포인트가
 * {@code metric} 파라미터를 받지 않는 것(rackId만 필수)으로 보아 7종 지표 전부를 한 번에 반환하는
 * "층×지표" 비교표로 해석해 아래와 같이 설계했다 — <b>A(메타) 확인 필요 사항</b>으로 보고에 남긴다.
 *
 * <p>데이터가 없는 (층, 지표) 조합도 비교표 완결성을 위해 생략하지 않고 {@code average=null,
 * state="IDLE"}로 채운다(series의 "빈 구간은 점 생략"과 달리, 이 응답은 시계열이 아니라 고정된
 * 층×지표 그리드라 행이 통째로 사라지면 표 형태가 깨진다).
 */
public record LevelSummaryResponse(
        Long rackId,
        String code,
        String range,
        boolean simulated,
        List<LevelRow> levels
) {

    public record LevelRow(Integer levelNo, String label, List<MetricCell> metrics) {
    }

    public record MetricCell(String metric, String unit, Double average, Double deviationPercent, String state) {
    }
}
