// 디자인 시안 프리뷰(이슈 #83)에서 여러 화면이 공유하는 표시 프리미티브.
// 색은 전부 globals.css의 --dp-* 토큰을 통하므로 라이트/다크가 자동으로 갈린다.
// 목업 화면이라 상호작용이 필요한 것(Toggle 등)만 상태를 밖에서 받는다.

import type { ReactNode } from "react";
import type { CellState } from "./mock";

/* ── 카드 ─────────────────────────────────────────────────────────────── */

export function Card({ className = "", children }: { className?: string; children: ReactNode }) {
  return (
    <div className={`rounded-[10px] border border-dp-line bg-dp-surface ${className}`}>{children}</div>
  );
}

export function CardTitle({ children, size = "md" }: { children: ReactNode; size?: "md" | "lg" }) {
  return (
    <span className={`${size === "lg" ? "text-[14px]" : "text-[13.5px]"} leading-none font-semibold text-dp-ink`}>
      {children}
    </span>
  );
}

/* ── 토글 스위치 ──────────────────────────────────────────────────────── */

export function Toggle({
  on,
  disabled = false,
  onChange,
  label,
  size = "md",
}: {
  on: boolean;
  disabled?: boolean;
  onChange?: () => void;
  /** 스크린리더용 이름 — 시각 라벨은 호출부가 따로 그린다 */
  label: string;
  size?: "sm" | "md";
}) {
  const track = size === "sm" ? "h-[19px] w-[34px]" : "h-[21px] w-[38px]";
  const knob = size === "sm" ? "h-[15px] w-[15px]" : "h-[17px] w-[17px]";
  const shift = size === "sm" ? "translate-x-[15px]" : "translate-x-[17px]";

  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      disabled={disabled}
      onClick={onChange}
      className={`relative inline-block flex-none rounded-full transition-colors ${track} ${
        disabled ? "cursor-not-allowed bg-dp-red-line" : on ? "bg-dp-green" : "bg-dp-track"
      }`}
    >
      <span
        className={`absolute top-[2px] left-[2px] rounded-full bg-white shadow-sm transition-transform ${knob} ${
          on ? shift : ""
        }`}
      />
    </button>
  );
}

/* ── 목표값 게이지 (시안 2a) ──────────────────────────────────────────── */

export function Gauge({ fill, marker }: { fill: number; marker: number }) {
  return (
    <div className="relative mt-3 h-[5px] rounded-[3px] bg-dp-track">
      <span
        className="absolute inset-y-0 left-0 rounded-[3px] bg-dp-green"
        style={{ width: `${fill}%` }}
      />
      <span
        className="absolute -top-1 h-[13px] w-[3px] rounded-[2px] bg-dp-ink"
        style={{ left: `${marker}%` }}
      />
    </div>
  );
}

/* ── 꺾은선 그래프 ────────────────────────────────────────────────────── */

export interface Line {
  points: string;
  /** --dp-* 토큰 이름(예: "green") */
  tone: "green" | "blue" | "amber" | "red" | "muted";
  width?: number;
  dashed?: boolean;
}

const LINE_STROKE: Record<Line["tone"], string> = {
  green: "var(--dp-green)",
  blue: "var(--dp-blue)",
  amber: "var(--dp-amber)",
  red: "var(--dp-red-ink)",
  muted: "var(--dp-muted)",
};

/**
 * 시안의 인라인 SVG를 그대로 옮긴 정적 스파크라인.
 * viewBox 좌표계를 늘려 쓰므로 preserveAspectRatio는 none 고정이다.
 */
export function LineChart({
  lines,
  viewBox,
  className = "",
  children,
}: {
  lines: Line[];
  viewBox: string;
  className?: string;
  /** 목표대 밴드·경보 구간 같은 배경 레이어 */
  children?: ReactNode;
}) {
  return (
    <div className={`relative ${className}`}>
      {children}
      <svg
        viewBox={viewBox}
        preserveAspectRatio="none"
        aria-hidden
        className="absolute inset-0 h-full w-full"
      >
        {lines.map((line, i) => (
          <polyline
            key={i}
            points={line.points}
            fill="none"
            stroke={LINE_STROKE[line.tone]}
            strokeWidth={line.width ?? 2}
            strokeDasharray={line.dashed ? "5 4" : undefined}
            vectorEffect="non-scaling-stroke"
          />
        ))}
      </svg>
    </div>
  );
}

/** 그래프 아래 X축 눈금 */
export function AxisLabels({ labels, className = "" }: { labels: string[]; className?: string }) {
  return (
    <div className={`mt-2 flex justify-between font-mono text-[10.5px] leading-none text-dp-muted ${className}`}>
      {labels.map((l) => (
        <span key={l}>{l}</span>
      ))}
    </div>
  );
}

/** 그래프 범례 한 칸 */
export function LegendKey({ tone, label }: { tone: Line["tone"]; label: string }) {
  return (
    <span className="font-mono text-[11px] leading-none font-semibold" style={{ color: LINE_STROKE[tone] }}>
      — {label}
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
 * 색만으로 상태를 구분하지 않도록 각 셀에 title/aria-label로 텍스트 상태를 함께 싣는다.
 */
export function RackGrid({
  cells,
  columns,
  selected,
  cellClass = "rounded-[4px]",
  gapClass = "gap-1.5",
  rowsClass,
}: {
  cells: CellState[][];
  columns: string[];
  selected?: { row: number; col: number };
  cellClass?: string;
  gapClass?: string;
  /** grid-template-rows 지정(모바일 축약형은 고정 높이) */
  rowsClass?: string;
}) {
  return (
    <div
      className={`grid flex-1 ${gapClass} ${rowsClass ?? ""}`}
      style={{
        gridTemplateColumns: `repeat(${columns.length}, minmax(0, 1fr))`,
        gridTemplateRows: rowsClass ? undefined : `repeat(${cells.length}, minmax(0, 1fr))`,
      }}
    >
      {cells.flatMap((row, r) =>
        row.map((state, c) => {
          const isSelected = selected?.row === r && selected?.col === c;
          return (
            <div
              key={`${r}-${c}`}
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
    <div className="flex gap-3 text-[11px] leading-none font-medium text-dp-body">
      {items.map((item) => (
        <span key={item.label} className="flex items-center gap-1.5">
          <span className={`h-[9px] w-[9px] rounded-[2px] ${CELL_BG[item.state]}`} />
          {item.label}
        </span>
      ))}
    </div>
  );
}

/* ── 작은 조각들 ──────────────────────────────────────────────────────── */

/** 상단 4~6칸 요약 타일 */
export function StatTile({
  label,
  value,
  unit,
  note,
  tone = "muted",
}: {
  label: string;
  value: string;
  unit?: string;
  note?: string;
  tone?: "ok" | "muted" | "alert";
}) {
  const valueColor = tone === "alert" ? "text-dp-red-ink" : "text-dp-ink";
  const noteColor = tone === "alert" ? "text-dp-red-ink" : tone === "ok" ? "text-dp-green" : "text-dp-muted";
  return (
    <Card className="px-3.5 py-3.5">
      <div className="text-[11.5px] leading-none font-medium text-dp-muted">{label}</div>
      <div className={`mt-2 mb-1.5 text-[25px] leading-none font-bold ${valueColor}`}>
        {value}
        {unit ? <span className="text-[13px] font-medium text-dp-muted">{unit}</span> : null}
      </div>
      {note ? <div className={`text-[11px] leading-none font-medium ${noteColor}`}>{note}</div> : null}
    </Card>
  );
}

/** 시안의 알약형 버튼/필터 칩. 목업이라 실제 동작이 없는 것은 span으로 그린다. */
export function Chip({
  children,
  active = false,
  tone = "neutral",
  as = "span",
  size = "md",
  onClick,
}: {
  children: ReactNode;
  active?: boolean;
  tone?: "neutral" | "critical" | "warning";
  as?: "span" | "button";
  /** md = 시안 기본(7/12), sm = 관리 화면 종류 탭(6/11) */
  size?: "md" | "sm";
  onClick?: () => void;
}) {
  const base =
    size === "sm"
      ? "rounded-md px-[11px] py-1.5 text-[11.5px] leading-none transition-colors"
      : "rounded-md px-3 py-[7px] text-[12px] leading-none transition-colors";
  const style = active
    ? "bg-dp-ink font-semibold text-dp-surface"
    : tone === "critical"
      ? "border border-dp-red-line bg-dp-red-tint font-semibold text-dp-red-ink"
      : tone === "warning"
        ? "border border-dp-amber-line bg-dp-amber-tint font-semibold text-dp-amber-deep"
        : "border border-dp-line-strong bg-dp-surface font-medium text-dp-body";
  const cls = `${base} ${style}`;
  if (as === "button") {
    return (
      <button type="button" onClick={onClick} aria-pressed={active} className={cls}>
        {children}
      </button>
    );
  }
  return <span className={cls}>{children}</span>;
}

// md = 시안의 본문 버튼(14/8·12.5px), sm = 툴바 버튼(13/7·12px).
// 크기는 prop으로만 정한다 — className으로 패딩을 덮으면 Tailwind 클래스 우선순위가
// 선언 순서에 좌우돼 결과가 불안정해진다.
const BUTTON_SIZE = {
  md: "px-3.5 py-2 text-[12.5px] font-semibold",
  sm: "px-[13px] py-[7px] text-[12px] font-medium",
} as const;

/** 채워진 초록 버튼 (목업 — 실제 액션 없음) */
export function PrimaryButton({
  children,
  size = "md",
  className = "",
}: {
  children: ReactNode;
  size?: keyof typeof BUTTON_SIZE;
  className?: string;
}) {
  return (
    <span
      className={`rounded-[7px] bg-dp-green leading-none text-dp-on-green ${BUTTON_SIZE[size]} ${className}`}
    >
      {children}
    </span>
  );
}

/** 외곽선 버튼 (목업 — 실제 액션 없음) */
export function GhostButton({
  children,
  size = "md",
  className = "",
}: {
  children: ReactNode;
  size?: keyof typeof BUTTON_SIZE;
  className?: string;
}) {
  return (
    <span
      className={`rounded-[7px] border border-dp-line-strong leading-none text-dp-body ${BUTTON_SIZE[size]} ${className}`}
    >
      {children}
    </span>
  );
}

/** 아바타 이니셜 원 */
export function Avatar({ initial, tone = "green", size = 26 }: { initial: string; tone?: "green" | "gray" | "faint"; size?: number }) {
  const bg = tone === "green" ? "bg-dp-green text-dp-on-green" : tone === "gray" ? "bg-[#7c8b96] text-white" : "bg-[#c9cec9] text-white";
  return (
    <span
      className={`flex flex-none items-center justify-center rounded-full font-semibold ${bg}`}
      style={{ width: size, height: size, fontSize: size * 0.42 }}
      aria-hidden
    >
      {initial}
    </span>
  );
}

/** 농장 카드/알람 행의 상태 점 */
export function StatusDot({ tone }: { tone: "critical" | "warning" | "done" }) {
  const bg = tone === "critical" ? "bg-dp-red" : tone === "warning" ? "bg-dp-amber" : "bg-dp-green";
  const label = tone === "critical" ? "경보" : tone === "warning" ? "주의" : "정상";
  return <span className={`h-[7px] w-[7px] flex-none rounded-full ${bg}`} title={label} aria-label={label} role="img" />;
}
