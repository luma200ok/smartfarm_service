"use client";

// 디자인 시안 프리뷰(이슈 #83) PC 화면의 공통 골격.
// 핸드오프 `Global Structure` 절 그대로: 상단 글로벌 바(56px 고정) + 좌측 서브 내비(240px) + 콘텐츠.
//
// 반응형은 핸드오프 `Responsive` 절의 4구간을 따른다.
//   fhd(≥1920) 기준       — 내비 240px, 패딩 26/30
//   lap(1440–1919) 노트북 — 내비 200px, 패딩 20/24
//   <1440 태블릿          — 좌측 내비를 상단 가로 메뉴로 접고 상세 패널은 아래로 쌓음
// 높이는 고정하지 않는다(1080은 설계 기준일 뿐, 카드가 늘어나고 세로 스크롤).
//
// 태블릿 구간의 "아이콘만(64px) 서브 내비"는 코드베이스에 아이콘 세트가 없어
// 가로 메뉴로 대체했다(핸드오프 Assets 절: 아이콘은 코드베이스 세트를 쓰라는 지시).

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { ACTIVE_FARM, NAV_SECTIONS } from "./mock";

/* ── 화면 프레임 ──────────────────────────────────────────────────────── */

export function Screen({ children }: { children: ReactNode }) {
  return <div className="min-h-dvh bg-dp-canvas font-dp-sans">{children}</div>;
}

/* ── 상단 글로벌 바 ───────────────────────────────────────────────────── */

export function TopBar({
  /** 농장 스위처 라벨. 알람처럼 다농장 화면은 "전체 농장 4곳" */
  farmLabel = ACTIVE_FARM,
  /** 우측 상태 텍스트들 (좁은 폭에서는 숨김) */
  status = [],
}: {
  farmLabel?: string;
  status?: ReactNode[];
}) {
  const pathname = usePathname() ?? "";

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3.5 bg-dp-bar px-5 text-white min-[1440px]:px-[22px]">
      <div className="flex-none text-[15px] leading-none font-bold tracking-[-0.02em]">
        스마트팜 <span className="text-[#5fd08c]">DFX</span>
      </div>
      <div className="hidden h-4 w-px flex-none bg-white/[0.18] min-[640px]:block" />
      <div className="hidden flex-none items-center gap-2 rounded-[7px] bg-white/10 px-3 py-1.5 text-[12.5px] leading-none font-medium min-[640px]:flex">
        {farmLabel} <span className="opacity-50">▾</span>
      </div>

      <nav className="ml-1 flex min-w-0 flex-1 gap-1 overflow-x-auto">
        {NAV_SECTIONS.map((section) => {
          const active = pathname.startsWith(section.href);
          return (
            <Link
              key={section.key}
              href={section.href}
              aria-current={active ? "page" : undefined}
              className={`rounded-md px-[13px] py-[7px] text-[12.5px] leading-none whitespace-nowrap transition-colors ${
                active ? "bg-white/[0.14] font-semibold text-white" : "text-white/65 hover:text-white/90"
              }`}
            >
              {section.label}
            </Link>
          );
        })}
      </nav>

      <div className="hidden flex-none items-center gap-3.5 text-[12px] leading-none text-white/70 min-[1440px]:flex">
        {status.map((s, i) => (
          <span key={i} className="flex items-center gap-1.5 whitespace-nowrap">
            {s}
          </span>
        ))}
      </div>
      <span className="flex h-[26px] w-[26px] flex-none items-center justify-center rounded-full bg-dp-green text-[11px] leading-none font-semibold text-dp-on-green">
        김
      </span>
    </header>
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

/** 상단 바 우측 `AI 챗봇` 버튼 */
export function ChatbotPill() {
  return <span className="rounded-md border border-white/25 px-3 py-1.5">AI 챗봇</span>;
}

/* ── 본문 (좌측 서브 내비 + 콘텐츠) ───────────────────────────────────── */

export function ScreenBody({
  /** NAV_SECTIONS의 key */
  section,
  /** 서브 내비 아래 붙는 보조 정보 블록 */
  aside,
  /** 서브 내비 메뉴와 보조 블록 사이를 밀어내면 하단 정렬 */
  asideBottom = false,
  children,
}: {
  section: string;
  aside?: ReactNode;
  asideBottom?: boolean;
  children: ReactNode;
}) {
  const nav = NAV_SECTIONS.find((s) => s.key === section);
  const items = nav?.items ?? [];

  return (
    // 콘텐츠가 짧아도 좌측 내비가 화면 하단까지 이어지도록 최소 높이를 준다(상단 바 56px 제외).
    <div className="flex min-h-[calc(100dvh-56px)]">
      {/* 기준·노트북 구간: 좌측 고정 내비 */}
      <div className="hidden w-[200px] flex-none flex-col gap-[3px] self-stretch border-r border-dp-line-2 bg-dp-surface px-3 py-4 min-[1440px]:flex min-[1920px]:w-[240px]">
        <div className="px-2 pb-2.5 font-mono text-[10.5px] leading-none font-semibold tracking-[0.06em] text-dp-muted">
          {nav?.label}
        </div>
        {items.map((item, i) => (
          <div
            key={item}
            aria-current={i === 0 ? "page" : undefined}
            className={
              i === 0
                ? "rounded-[7px] border border-dp-green-line bg-dp-green-tint px-3 py-2.5 text-[12.5px] leading-none font-semibold text-dp-green-ink"
                : "px-3 py-2.5 text-[12.5px] leading-none text-dp-body"
            }
          >
            {item}
          </div>
        ))}
        {asideBottom ? <div className="flex-1" /> : null}
        {aside}
      </div>

      <main className="flex min-w-0 flex-1 flex-col gap-3.5 px-4 py-4 min-[768px]:px-5 min-[1440px]:gap-3.5 min-[1440px]:px-6 min-[1440px]:py-5 min-[1920px]:gap-4 min-[1920px]:px-[30px] min-[1920px]:py-[26px]">
        {/* 태블릿 이하: 서브 내비를 상단 가로 메뉴로 */}
        <div className="-mx-4 flex gap-1.5 overflow-x-auto px-4 min-[768px]:-mx-5 min-[768px]:px-5 min-[1440px]:hidden">
          {items.map((item, i) => (
            <span
              key={item}
              aria-current={i === 0 ? "page" : undefined}
              className={`rounded-[7px] px-3 py-2 text-[12.5px] leading-none whitespace-nowrap ${
                i === 0
                  ? "border border-dp-green-line bg-dp-green-tint font-semibold text-dp-green-ink"
                  : "border border-dp-line bg-dp-surface text-dp-body"
              }`}
            >
              {item}
            </span>
          ))}
        </div>
        {children}
      </main>
    </div>
  );
}

/** 콘텐츠 상단 제목 줄 */
export function PageTitle({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-2.5">
      <h1 className="text-[17px] leading-[1.2] font-bold text-dp-ink">{title}</h1>
      {children}
    </div>
  );
}

/**
 * 메인 + 우측 상세 패널 2열. 기준 폭에서만 패널이 옆에 서고,
 * 태블릿 이하에서는 아래로 쌓인다(핸드오프 Responsive).
 * fhd 폭은 화면마다 다르다 — 홈 400 / 제어·부가 430 / 알람·관리 440.
 */
export const DETAIL_COLS = {
  400: "grid gap-3.5 min-[1440px]:grid-cols-[1fr_340px] min-[1920px]:grid-cols-[1fr_400px]",
  430: "grid gap-3.5 min-[1440px]:grid-cols-[1fr_340px] min-[1920px]:grid-cols-[1fr_430px]",
  440: "grid gap-3.5 min-[1440px]:grid-cols-[1fr_340px] min-[1920px]:grid-cols-[1fr_440px]",
} as const;
