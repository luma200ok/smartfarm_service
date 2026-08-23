package com.smartfarm.service.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /readings/latest} 랙 도면 셀 응답(contract §4.11) — 활성(비삭제) 랙·층만 렌더한다
 * (§4.10 soft delete된 구조를 참조하는 이력은 정상 상태이지만, 이 응답은 현재 살아있는 구조만
 * 보여준다 — handoff 요건 3). {@code state}는 {@code CellState}(OK/WARNING/CRITICAL/IDLE)와
 * 1:1 대응 — IDLE은 해당 층에 값이 없거나(데이터 없음), 값이 있어도 신선도 상한을 넘겨(사이클 2
 * 리뷰 P2-2) 현재값으로 취급하지 않을 때다.
 */
public record ReadingMatrixResponse(
        String metric,
        String unit,
        boolean simulated,
        List<RackRow> racks
) {

    public record RackRow(Long rackId, String code, List<LevelCell> levels) {
    }

    /**
     * {@code measuredAt}은 신선도 상한으로 {@code state=IDLE}·{@code value=null}로 떨어뜨린
     * 경우에도 <b>실제 마지막 측정 시각을 그대로 싣는다</b>(값만 "현재값 아님"으로 감추고, 마지막
     * 측정이 언제였는지는 프론트가 판단할 수 있게 남긴다 — contract §4.11 "measuredAt도 함께
     * 실어 프론트가 판단할 수 있게 한다").
     */
    public record LevelCell(Integer levelNo, Double value, LocalDateTime measuredAt, String state) {
    }
}
