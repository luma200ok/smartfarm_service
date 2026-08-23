// 디자인 시안 프리뷰(이슈 #83)에서 여러 화면이 공유하는 표시 프리미티브.
// 값의 출처는 핸드오프 `design_handoff_smartfarm_dfx/README.md`의 Design Tokens 절이고,
// 색은 전부 globals.css의 --dp-* 토큰을 통하므로 라이트/다크가 자동으로 갈린다.
//
// Card·CardTitle·Chip·StatusBadge·RackGrid·RackLegend는 운영 화면(FarmRackPanel 등)도 함께
// 쓰면서 components/monitoring/ui.tsx로 승격했다(이슈 #99 리뷰 — 운영 코드가 프리뷰 모듈에
// 의존하는 걸 막기 위해 방향을 뒤집음). 이 파일은 프리뷰 10화면의 기존 import 경로가 깨지지
// 않도록 그대로 재노출만 한다.
export { Card, CardTitle, Chip, RackGrid, RackLegend, StatusBadge } from "@/components/monitoring/ui";

import type { ReactNode } from "react";

/** 홈 화면 카드만 radius 11px (핸드오프 Global Structure) — 프리뷰 홈 전용이라 승격 대상 아님. */
export function HomeCard({ className = "", children }: { className?: string; children: ReactNode }) {
  return <div className={`rounded-[11px] border border-dp-line bg-dp-surface ${className}`}>{children}</div>;
}

/* ── 버튼 / 칩 ────────────────────────────────────────────────────────── */

const BUTTON_SIZE = {
  md: "px-3.5 py-2 text-[12.5px] font-semibold",
  sm: "px-[13px] py-[7px] text-[12px] font-medium",
} as const;

/** 채워진 초록 버튼. 목업이라 표시 전용이다(서버로 나가는 동작 없음). */
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
      className={`rounded-[7px] bg-dp-green leading-none whitespace-nowrap text-dp-on-green ${BUTTON_SIZE[size]} ${className}`}
    >
      {children}
    </span>
  );
}

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
      className={`rounded-[7px] border border-dp-line-strong leading-none whitespace-nowrap text-dp-body ${BUTTON_SIZE[size]} ${className}`}
    >
      {children}
    </span>
  );
}

/** 요약 pill (홈 브리핑 행) */
export function Pill({ label, alert = false }: { label: string; alert?: boolean }) {
  return (
    <span
      className={`flex items-center gap-2 rounded-[20px] bg-dp-surface px-[13px] py-[7px] text-[12px] leading-none whitespace-nowrap ${
        alert
          ? "border border-dp-red-line font-semibold text-dp-red-ink"
          : "border border-dp-line-2 font-medium text-dp-body"
      }`}
    >
      {alert ? <span className="h-[7px] w-[7px] rounded-full bg-dp-red" /> : null}
      {label}
    </span>
  );
}

/**
 * 이상값 마커 — 지표 값 옆에 붙여 색 외의 수단으로도 이상 상태를 전달한다(WCAG 1.4.1,
 * 이슈 #88). 색은 그대로 유지하고 마커는 보강 수단이므로 부모가 이미 색으로 상태를 표시한
 * 자리에서만 쓴다. 값 옆에 스크린리더용 상태 텍스트(sr-only)가 함께 오는 경우가 많아
 * 중복 낭독을 막기 위해 aria-hidden을 준다.
 */
export function AlertMarker({ className = "" }: { className?: string }) {
  return (
    <span aria-hidden className={`ml-0.5 ${className}`}>
      !
    </span>
  );
}

/* ── 토글 ─────────────────────────────────────────────────────────────── */

/** 핸드오프 Fixed dimensions: 트랙 38×21 / 노브 17. 모바일은 44×26 / 노브 20. */
export function Toggle({
  on,
  disabled = false,
  blocked = false,
  onChange,
  label,
  size = "md",
}: {
  on: boolean;
  /** 완전 비활성 — 클릭 이벤트 자체가 발생하지 않는다 */
  disabled?: boolean;
  /** 비활성 모양이지만 클릭은 받는다(통신 두절 안내를 띄우기 위함) */
  blocked?: boolean;
  onChange?: () => void;
  /** 스크린리더용 이름 — 시각 라벨은 호출부가 따로 그린다 */
  label: string;
  size?: "sm" | "md" | "touch";
}) {
  const dims = {
    sm: { track: "h-[19px] w-[34px]", knob: "h-[15px] w-[15px]", shift: "translate-x-[15px]" },
    md: { track: "h-[21px] w-[38px]", knob: "h-[17px] w-[17px]", shift: "translate-x-[17px]" },
    touch: { track: "h-[26px] w-[44px]", knob: "h-5 w-5", shift: "translate-x-[18px]" },
  }[size];

  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      aria-disabled={blocked || undefined}
      disabled={disabled}
      onClick={onChange}
      className={`relative inline-block flex-none rounded-full transition-colors ${dims.track} ${
        disabled || blocked ? "cursor-not-allowed bg-dp-red-off" : on ? "bg-dp-green" : "bg-dp-off"
      }`}
    >
      <span
        className={`absolute top-[2px] left-[2px] rounded-full bg-white shadow-sm transition-transform ${dims.knob} ${
          on ? dims.shift : ""
        }`}
      />
    </button>
  );
}

/* ── 진행 바 ──────────────────────────────────────────────────────────── */

export function Gauge({ fill, marker, tall = false }: { fill: number; marker: number; tall?: boolean }) {
  return (
    <div className={`relative rounded-[3px] bg-dp-track ${tall ? "h-1.5" : "h-[5px]"}`}>
      <span className="absolute inset-y-0 left-0 rounded-[3px] bg-dp-green" style={{ width: `${fill}%` }} />
      <span
        className={`absolute w-[3px] rounded-[2px] bg-dp-ink ${tall ? "-top-[5px] h-4" : "-top-1 h-[13px]"}`}
        style={{ left: `${marker}%` }}
      />
    </div>
  );
}

/* ── 미니 막대 그래프 (농장 카드 7일 추이) ────────────────────────────── */

const BAR_TONE = {
  past: "bg-dp-bar-past",
  recent: "bg-dp-green-line",
  latest: "bg-dp-green-mid",
  critical: "bg-dp-red",
  warning: "bg-dp-amber",
  "warning-mid": "bg-dp-amber-mid",
} as const;

export function MiniBars({ bars }: { bars: { height: number; tone: keyof typeof BAR_TONE }[] }) {
  return (
    <div className="flex h-[34px] items-end gap-[3px]" aria-hidden>
      {bars.map((bar, i) => (
        <span key={i} className={`flex-1 rounded-[2px] ${BAR_TONE[bar.tone]}`} style={{ height: `${bar.height}%` }} />
      ))}
    </div>
  );
}

/* ── 꺾은선 그래프 ────────────────────────────────────────────────────── */

export interface Line {
  points: string;
  tone: "green" | "blue" | "amber" | "red" | "neutral";
  width?: number;
  dashed?: boolean;
}

const LINE_STROKE: Record<Line["tone"], string> = {
  green: "var(--dp-green)",
  blue: "var(--dp-blue)",
  amber: "var(--dp-amber)",
  red: "var(--dp-red-ink)",
  neutral: "var(--dp-neutral)",
};

/**
 * 핸드오프 Assets 절: 프로토타입의 인라인 SVG를 그대로 옮긴 정적 그래프.
 * 실제 구현 시 차트 라이브러리로 교체하되 색과 선 두께는 유지한다.
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
      <svg viewBox={viewBox} preserveAspectRatio="none" aria-hidden className="absolute inset-0 h-full w-full">
        {lines.map((line, i) => (
          <polyline
            key={i}
            points={line.points}
            fill="none"
            stroke={LINE_STROKE[line.tone]}
            strokeWidth={line.width ?? 2}
            strokeDasharray={line.dashed ? (line.width && line.width < 2 ? "4 3" : "5 4") : undefined}
            vectorEffect="non-scaling-stroke"
          />
        ))}
      </svg>
    </div>
  );
}

/** 그래프 격자 배경 — 25% / 20% 간격 */
export const GRID_25 = "bg-[linear-gradient(to_top,var(--dp-line)_1px,transparent_1px)] bg-[length:100%_25%]";
export const GRID_20 = "bg-[linear-gradient(to_top,var(--dp-line)_1px,transparent_1px)] bg-[length:100%_20%]";

export function AxisLabels({ labels, className = "" }: { labels: string[]; className?: string }) {
  return (
    <div className={`flex justify-between font-mono text-[10.5px] leading-none text-dp-muted ${className}`}>
      {labels.map((l) => (
        <span key={l}>{l}</span>
      ))}
    </div>
  );
}

export function LegendKey({ tone, label }: { tone: Line["tone"]; label: string }) {
  return (
    <span
      className="font-mono text-[10.5px] leading-none font-semibold whitespace-nowrap"
      style={{ color: LINE_STROKE[tone] }}
    >
      — {label}
    </span>
  );
}

/* ── 작은 조각들 ──────────────────────────────────────────────────────── */

/** 아바타 이니셜 원 — 헤더 26px, 목록 28px */
export function Avatar({
  initial,
  tone = "green",
  size = 26,
}: {
  initial: string;
  tone?: "green" | "gray" | "faint";
  size?: number;
}) {
  const bg =
    tone === "green"
      ? "bg-dp-green text-dp-on-green"
      : tone === "gray"
        ? "bg-dp-neutral text-white"
        : "bg-dp-disabled text-white";
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

/** 시각 42px + 내용 — 처리 이력 / 시스템 로그 / 적용 이력 공통 행 */
export function TimelineRow({
  time,
  text,
  dim = false,
  last = false,
}: {
  time: string;
  text: string;
  dim?: boolean;
  last?: boolean;
}) {
  return (
    <div className={`flex gap-2.5 py-[7px] ${last ? "" : "border-b border-dp-line-row"}`}>
      <span className="w-[42px] flex-none font-mono text-[11px] leading-[1.4] font-medium text-dp-muted">{time}</span>
      <span
        className={`flex-1 text-[12px] leading-[1.4] ${dim ? "font-normal text-dp-sub" : "font-medium text-dp-ink"}`}
      >
        {text}
      </span>
    </div>
  );
}

/** 카드 안 안내/근거 블록 */
export function NoteBlock({
  children,
  tone = "plain",
  className = "",
}: {
  children: ReactNode;
  tone?: "plain" | "warning";
  className?: string;
}) {
  const style =
    tone === "warning"
      ? "border border-dp-amber-line bg-dp-amber-tint font-medium text-dp-amber-sub"
      : "bg-dp-inset font-normal text-dp-sub";
  return <p className={`rounded-lg px-3 py-2.5 text-[11.5px] leading-[1.6] ${style} ${className}`}>{children}</p>;
}
