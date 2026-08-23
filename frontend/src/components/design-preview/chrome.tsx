"use client";

// 디자인 시안 프리뷰(이슈 #83)의 공통 골격 — 상단 글로벌 바 + 좌측 서브메뉴 + 콘텐츠.
// 시안 1b/2a~2e가 전부 같은 규칙을 쓰므로 한 곳에 모았다.
// 상단 바의 대분류는 실제로 프리뷰 라우트 간 이동을 하고, 좌측 서브메뉴는 목업이라
// 첫 항목만 활성 표시하고 나머지는 비활성으로 둔다.

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { ACTIVE_FARM, NAV_SECTIONS } from "./mock";

/* ── 상단 글로벌 바 ───────────────────────────────────────────────────── */

export function TopBar({
  /** 농장 스위처에 표시할 이름. 알람 화면처럼 전체 농장을 보는 화면은 "전체 농장 4곳" */
  farmLabel = ACTIVE_FARM,
  /** 우측 상태 텍스트들 */
  status = [],
  /** 시안 1b는 56px, 2a~2e는 52px */
  height = 52,
  compact = false,
}: {
  farmLabel?: string;
  status?: ReactNode[];
  height?: number;
  compact?: boolean;
}) {
  const pathname = usePathname() ?? "";

  return (
    <div
      className="flex flex-none items-center gap-3.5 bg-dp-bar px-5 text-white"
      style={{ height }}
    >
      <div className="text-[14px] leading-none font-bold tracking-[-0.02em]">
        스마트팜 <span className="text-[#5fd08c]">DFX</span>
      </div>
      <div className="h-[15px] w-px bg-white/20" />
      <div className="rounded-[7px] bg-white/10 px-2.5 py-1.5 text-[12px] leading-none font-medium">
        {farmLabel} <span className="opacity-50">▾</span>
      </div>

      <nav className={`ml-2 flex ${compact ? "gap-0.5" : "gap-1"}`}>
        {NAV_SECTIONS.map((section) => {
          const active = section.href !== null && pathname.startsWith(section.href);
          const cls = `rounded-md px-3 py-1.5 text-[12.5px] leading-none transition-colors ${
            active ? "bg-white/15 font-semibold text-white" : "text-white/60 hover:text-white/90"
          }`;
          return section.href ? (
            <Link key={section.label} href={section.href} aria-current={active ? "page" : undefined} className={cls}>
              {section.label}
            </Link>
          ) : (
            <span key={section.label} className={cls}>
              {section.label}
            </span>
          );
        })}
      </nav>

      <div className="flex-1" />
      <div className="flex items-center gap-3 text-[11.5px] leading-none text-white/65">
        {status.map((s, i) => (
          <span key={i} className="flex items-center gap-1.5">
            {s}
          </span>
        ))}
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-dp-green text-[10.5px] leading-none font-semibold text-dp-on-green">
          김
        </span>
      </div>
    </div>
  );
}

/** 상단 바 알람 배지 */
export function AlarmBadge({ count }: { count: number }) {
  return (
    <>
      알람
      <span className="rounded-[9px] bg-dp-red px-[7px] py-0.5 font-mono text-[10px] leading-[1.4] font-semibold text-white">
        {count}
      </span>
    </>
  );
}

/* ── 좌측 서브메뉴 ────────────────────────────────────────────────────── */

export function SubNav({
  section,
  /** 활성 항목 인덱스 (목업이라 기본 0번) */
  activeIndex = 0,
  children,
}: {
  /** NAV_SECTIONS의 label */
  section: string;
  activeIndex?: number;
  /** 메뉴 아래 붙는 보조 블록 */
  children?: ReactNode;
}) {
  const items = NAV_SECTIONS.find((s) => s.label === section)?.items ?? [];

  return (
    <div className="flex w-[196px] flex-none flex-col gap-[3px] border-r border-dp-line bg-dp-surface px-3 py-4">
      <div className="px-2 pb-2.5 font-mono text-[10.5px] leading-none font-semibold tracking-[0.06em] text-dp-muted">
        {section}
      </div>
      {items.map((item, i) => (
        <div
          key={item}
          aria-current={i === activeIndex ? "page" : undefined}
          className={
            i === activeIndex
              ? "rounded-[7px] border border-dp-green-line bg-dp-green-tint px-3 py-2.5 text-[12.5px] leading-none font-semibold text-dp-green-ink"
              : "px-3 py-2.5 text-[12.5px] leading-none text-dp-body"
          }
        >
          {item}
        </div>
      ))}
      {children}
    </div>
  );
}

/* ── 화면 프레임 ──────────────────────────────────────────────────────── */

/**
 * 시안은 1280·1440 고정폭 아트보드다. 여기서는 세로는 뷰포트에 맞춰 늘리고 가로는
 * 최소폭만 지켜 그 아래에서는 가로 스크롤이 생기게 했다(PC 전용 화면이라 축소 재배치를 하지 않음).
 */
export function Screen({
  minWidth = 1180,
  children,
}: {
  minWidth?: number;
  children: ReactNode;
}) {
  return (
    <div className="h-dvh overflow-x-auto overflow-y-hidden bg-dp-canvas">
      <div className="flex h-full flex-col bg-dp-canvas font-dp-sans" style={{ minWidth }}>
        {children}
      </div>
    </div>
  );
}

/** 상단 바 아래 본문 영역(좌측 서브메뉴 + 콘텐츠) */
export function ScreenBody({ children }: { children: ReactNode }) {
  return <div className="flex min-h-0 flex-1">{children}</div>;
}

/** 서브메뉴 오른쪽 콘텐츠 컬럼 */
export function ScreenMain({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`flex min-w-0 flex-1 flex-col gap-3.5 overflow-hidden px-5 py-4.5 ${className}`}>
      {children}
    </div>
  );
}

/** 콘텐츠 상단 제목 줄 */
export function PageTitle({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex items-center gap-2.5">
      <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">{title}</h1>
      {children}
    </div>
  );
}
