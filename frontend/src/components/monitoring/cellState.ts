// 백엔드 ReadingCellState("OK"|"WARNING"|"CRITICAL"|"IDLE", contract §4.11)를
// design-preview/ui.tsx의 RackGrid가 쓰는 CellState(소문자, "ok-soft" 포함)로 매핑.
// RackGrid·RackLegend는 그대로 재사용하고(handoff 요건 — 공통 셸 재사용, 복붙 금지) 값만 변환한다.
import type { CellState } from "@/components/design-preview/mock";
import type { ReadingCellState } from "@/types";

export function toCellState(state: ReadingCellState): CellState {
  switch (state) {
    case "OK":
      return "ok";
    case "WARNING":
      return "warning";
    case "CRITICAL":
      return "critical";
    case "IDLE":
      return "idle";
    default:
      return "idle";
  }
}
