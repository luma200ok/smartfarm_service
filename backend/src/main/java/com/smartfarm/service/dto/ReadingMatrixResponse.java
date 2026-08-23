package com.smartfarm.service.dto;

import java.util.List;

/**
 * {@code GET /readings/latest} 랙 도면 셀 응답(contract §4.11) — 활성(비삭제) 랙·층만 렌더한다
 * (§4.10 soft delete된 구조를 참조하는 이력은 정상 상태이지만, 이 응답은 현재 살아있는 구조만
 * 보여준다 — handoff 요건 3). {@code state}는 {@code CellState}(OK/WARNING/CRITICAL/IDLE)와
 * 1:1 대응 — IDLE은 해당 층에 값이 없을 때(데이터 없음)다.
 */
public record ReadingMatrixResponse(
        String metric,
        String unit,
        boolean simulated,
        List<RackRow> racks
) {

    public record RackRow(Long rackId, String code, List<LevelCell> levels) {
    }

    public record LevelCell(Integer levelNo, Double value, String state) {
    }
}
