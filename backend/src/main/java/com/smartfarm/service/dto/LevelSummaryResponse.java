package com.smartfarm.service.dto;

import java.util.List;

/**
 * {@code GET /readings/level-summary} 응답(contract §4.11) — "층별 평균 + 목표 대비 편차 —
 * 층×지표 그리드"로 2026-08-23 사이클 2에서 계약에 승격된 shape다(초판은 필드가 계약서에
 * 명시되지 않아 구현이 추정 설계했으나, 이후 그 설계 그대로 계약에 반영됐다). 엔드포인트가
 * {@code metric} 파라미터를 받지 않는 것(rackId만 필수)에 맞춰 7종 지표 전부를 한 번에 반환한다.
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
