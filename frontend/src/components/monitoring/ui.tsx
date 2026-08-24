// 운영 화면 공용 UI 프리미티브(이슈 #99 리뷰 반영) — 원래 design-preview/ui.tsx에 있던 것을
// 운영용으로 승격했다. 색은 전부 globals.css의 --dp-* 토큰을 통하므로 라이트/다크가 자동으로
// 갈린다(토큰 자체는 건드리지 않음). design-preview/ui.tsx는 이 파일을 거꾸로 재노출해
// 프리뷰 10화면의 기존 import 경로를 그대로 유지한다(의존 방향: 프리뷰 → 운영 공용).
import type { ReactNode } from "react";
import type { PreviewCellState as CellState, PreviewSeverity } from "@/types";

// StatusBadge 전용 확장 톤 — "neutral"은 위험/경고가 아닌 중립 상태 표기용(제어 화면 #108의
// OFF — 장애가 아니라 조작 결과라 critical 톤을 쓰면 고장으로 오인된다). PreviewSeverity
// 자체를 넓히면 design-preview의 Record<PreviewSeverity, …> 완전성 매핑이 전부 깨지므로
// (그 화면들은 손대지 않는다), StatusBadge의 tone에서만 로컬로 확장한다.
type Severity = PreviewSeverity | "neutral";

/* ── 카드 ─────────────────────────────────────────────────────────────── */

/**
 * `as`는 기본 "div"(기존 소비처 전부가 이 기본 동작에 의존하므로 바꾸지 않는다) — 원래
 * `<section>`이었던 자리를 옮길 때만 "section"을 지정해 랜드마크 소실을 막는다
 * (이슈 #109 리뷰 P3-1, CardTitle의 `as`와 같은 패턴).
 */
export function Card({
  className = "",
  as: Tag = "div",
  children,
}: {
  className?: string;
  as?: "div" | "section";
  children: ReactNode;
}) {
  return <Tag className={`rounded-[10px] border border-dp-line bg-dp-surface ${className}`}>{children}</Tag>;
}

/**
 * `as`는 기본 "span"(design-preview 10화면·기존 운영 화면이 이 기본 동작에 의존하므로 바꾸지
 * 않는다) — 실제 문서 헤딩 자리에 쓸 때만 "h2"/"h3"를 지정해 스크린리더 헤딩 탐색(WCAG 1.3.1)이
 * 카드 제목을 건너뛰지 않게 한다(이슈 #109 리뷰 P2, ui.tsx는 이번에 처음 손댐 — 다른 그룹과 충돌 없음).
 */
export function CardTitle({
  children,
  size = "md",
  as: Tag = "span",
}: {
  children: ReactNode;
  size?: "md" | "lg";
  as?: "span" | "h2" | "h3";
}) {
  return (
    <Tag className={`${size === "lg" ? "text-[14px]" : "text-[13.5px]"} leading-none font-semibold text-dp-ink`}>
      {children}
    </Tag>
  );
}

/** 세그먼트/필터 칩. 크기는 prop으로만 정한다(className 패딩 오버라이드는 우선순위가 불안정). */
export function Chip({
  children,
  active = false,
  tone = "neutral",
  as = "span",
  size = "md",
  disabled = false,
  onClick,
}: {
  children: ReactNode;
  active?: boolean;
  tone?: "neutral" | "critical" | "warning";
  as?: "span" | "button";
  size?: "md" | "sm";
  /** as="button"일 때만 의미가 있다(busy 중 중복 클릭 방지 — 제어 화면 #108에서 추가). */
  disabled?: boolean;
  onClick?: () => void;
}) {
  const base =
    size === "sm"
      ? "rounded-md px-[11px] py-1.5 text-[11.5px] leading-none whitespace-nowrap transition-colors"
      : "rounded-md px-3 py-[7px] text-[12px] leading-none whitespace-nowrap transition-colors";
  const style = active
    ? "bg-dp-ink font-semibold text-dp-surface"
    : tone === "critical"
      ? "border border-dp-red-line bg-dp-red-tint font-semibold text-dp-red-ink"
      : tone === "warning"
        ? "border border-dp-amber-line bg-dp-amber-tint font-semibold text-dp-amber-deep"
        : "border border-dp-line-strong bg-dp-surface font-medium text-dp-body";
  const cls = `${base} ${style} ${disabled ? "opacity-40" : ""}`;
  if (as === "button") {
    return (
      <button type="button" disabled={disabled} onClick={onClick} aria-pressed={active} className={cls}>
        {children}
      </button>
    );
  }
  return <span className={cls}>{children}</span>;
}

/** 상태 배지 (농장 카드). "neutral"은 위험/경고가 아닌 중립 상태 표기용(제어 화면 #108의
 * OFF — 장애가 아니라 조작 결과라 critical 톤을 쓰면 고장으로 오인된다). */
export function StatusBadge({ label, tone }: { label: string; tone: Severity }) {
  const style =
    tone === "critical"
      ? "bg-dp-red-tint-2 text-dp-red-ink"
      : tone === "warning"
        ? "bg-dp-amber-tint text-dp-amber-deep"
        : tone === "neutral"
          ? "bg-dp-badge-neutral text-dp-muted"
          : "bg-dp-green-tint-2 text-dp-green-ink";
  return (
    <span className={`flex-none rounded-[5px] px-[9px] py-1 text-[10.5px] leading-[1.4] font-semibold ${style}`}>
      {label}
    </span>
  );
}

/* ── 랙 배치도 ────────────────────────────────────────────────────────── */

const CELL_BG: Record<CellState, string> = {
  ok: "bg-dp-green",
  "ok-soft": "bg-dp-green-soft",
  warning: "bg-dp-amber",
  critical: "bg-dp-red",
  idle: "bg-dp-idle",
};

const CELL_LABEL: Record<CellState, string> = {
  ok: "정상",
  "ok-soft": "정상",
  warning: "주의",
  critical: "경보",
  idle: "미가동",
};

/**
 * 셀 = 랙 1개 층. 색은 목표 대비 편차.
 * 좁은 폭에서는 셀을 줄이는 대신 가로 스크롤을 허용하고 최소 변 길이 14px를 지킨다
 * (핸드오프 Responsive 원칙).
 * 색만으로 상태를 구분하지 않도록 각 셀에 텍스트 상태를 싣는다 — 다만 role 없는 div에 붙인
 * aria-label은 스크린리더가 이름 계산을 하지 않으므로 role="img"를 함께 준다.
 */
export function RackGrid({
  cells,
  columns,
  selected,
  minCell = 14,
  cellClass = "rounded-[4px]",
  gapClass = "gap-[5px]",
  rowHeight,
}: {
  cells: CellState[][];
  columns: string[];
  selected?: { row: number; col: number };
  minCell?: number;
  cellClass?: string;
  gapClass?: string;
  /** 지정하면 행 높이를 고정(모바일 축약형) */
  rowHeight?: number;
}) {
  return (
    <div
      className={`grid flex-1 ${gapClass}`}
      style={{
        gridTemplateColumns: `repeat(${columns.length}, minmax(${minCell}px, 1fr))`,
        gridTemplateRows: rowHeight
          ? `repeat(${cells.length}, ${rowHeight}px)`
          : `repeat(${cells.length}, minmax(${minCell}px, 1fr))`,
      }}
    >
      {cells.flatMap((row, r) =>
        row.map((state, c) => {
          const isSelected = selected?.row === r && selected?.col === c;
          return (
            <div
              key={`${r}-${c}`}
              role="img"
              title={`${columns[c]} · ${cells.length - r}층 · ${CELL_LABEL[state]}`}
              aria-label={`${columns[c]} ${cells.length - r}층 ${CELL_LABEL[state]}`}
              className={`${CELL_BG[state]} ${cellClass} ${
                isSelected ? "outline-2 outline-offset-1 outline-dp-ink" : ""
              }`}
            />
          );
        }),
      )}
    </div>
  );
}

export function RackLegend({ items }: { items: { state: CellState; label: string }[] }) {
  return (
    <div className="flex gap-3.5 text-[11px] leading-none font-medium text-dp-sub">
      {items.map((item) => (
        <span key={item.label} className="flex items-center gap-[5px] whitespace-nowrap">
          <span className={`h-[9px] w-[9px] rounded-[2px] ${CELL_BG[item.state]}`} />
          {item.label}
        </span>
      ))}
    </div>
  );
}
