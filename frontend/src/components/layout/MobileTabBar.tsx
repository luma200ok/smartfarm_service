"use client";

import Link from "next/link";

// 모바일 하단 고정 탭 4개(이슈 #147, 시안 m1~m4) — 홈 / 제어 / 알람 / 더보기. 데이터·부가서비스·
// 관리는 더보기 안으로 접는다(핸드오프 §1). 탭당 터치 타깃 44px 이상(핸드오프 §1 — 세로
// padding 8px×2 + 아이콘 20px + 라벨 11px로 이미 44px를 넘는다, 명시적 min-h로 보장).
export type MobileTabKey = "home" | "control" | "alarm" | "more";

interface Tab {
  key: MobileTabKey;
  label: string;
  href: string | null;
}

interface MobileTabBarProps {
  active: MobileTabKey;
  homeHref: string;
  controlHref: string | null;
  alarmHref: string | null;
  className?: string;
}

export default function MobileTabBar({
  active,
  homeHref,
  controlHref,
  alarmHref,
  className = "",
}: MobileTabBarProps) {
  const tabs: Tab[] = [
    { key: "home", label: "홈", href: homeHref },
    { key: "control", label: "제어", href: controlHref },
    { key: "alarm", label: "알람", href: alarmHref },
    { key: "more", label: "더보기", href: "/more" },
  ];

  return (
    <nav
      aria-label="모바일 하단 내비게이션"
      className={`sticky bottom-0 z-40 grid flex-none grid-cols-4 gap-0.5 border-t border-dp-line-2 bg-dp-surface px-2 pt-[9px] pb-[26px] ${className}`}
    >
      {tabs.map((tab) => {
        const isActive = tab.key === active;
        const content = (
          <>
            <span
              aria-hidden="true"
              className={`h-5 w-5 rounded-[5px] ${isActive ? "bg-dp-green" : "bg-dp-disabled"}`}
            />
            <span
              className={`text-[11px] leading-none ${isActive ? "font-semibold text-dp-green-ink" : "font-medium text-dp-muted"}`}
            >
              {tab.label}
            </span>
          </>
        );
        if (!tab.href) {
          return (
            <span
              key={tab.key}
              aria-disabled="true"
              className="flex min-h-11 flex-col items-center justify-center gap-1.5 py-2 opacity-40"
            >
              {content}
            </span>
          );
        }
        return (
          <Link
            key={tab.key}
            href={tab.href}
            aria-current={isActive ? "page" : undefined}
            className="flex min-h-11 flex-col items-center justify-center gap-1.5 py-2"
          >
            {content}
          </Link>
        );
      })}
    </nav>
  );
}
