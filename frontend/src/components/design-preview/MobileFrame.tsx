// 모바일 화면(M1~M4)의 공통 골격 — 핸드오프 `Mobile` 절.
// PC를 축소한 게 아니라 별도 레이아웃이고, 기준 프레임은 390 × 844(하단 세이프 에어리어 포함).
// 실제 모바일 폭(<768)에서는 화면을 꽉 채우고, 데스크톱에서는 390px 프레임을 가운데 띄워
// 시안과 같은 조건으로 볼 수 있게 한다.
//
// 클라이언트 훅을 쓰지 않으므로 "use client"를 두지 않는다 — 이 파일이 클라이언트 경계가 되면
// 상호작용이 없는 M1(홈)·M4(더보기)까지 클라이언트 번들로 끌려간다.

import Link from "next/link";
import type { ReactNode } from "react";
import { MOBILE_TABS } from "./mock";

export function MobileFrame({
  /** 다크 상단 바 내용 */
  header,
  /** 하단 고정 액션 바(제어 화면) — 탭 바 위에 붙는다 */
  bottomAction,
  activeTab,
  children,
}: {
  header: ReactNode;
  bottomAction?: ReactNode;
  activeTab: string;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-dvh justify-center bg-dp-canvas font-dp-sans min-[768px]:items-center min-[768px]:px-4 min-[768px]:py-10">
      <div className="flex w-full flex-col bg-dp-canvas min-[768px]:h-[844px] min-[768px]:w-[390px] min-[768px]:overflow-hidden min-[768px]:rounded-[10px] min-[768px]:border min-[768px]:border-dp-line min-[768px]:shadow-sm">
        <div className="flex-none bg-dp-bar text-white">{header}</div>

        <div className="flex flex-1 flex-col gap-3 overflow-y-auto px-4 py-3.5">{children}</div>

        {bottomAction ? (
          <div className="flex-none border-t border-dp-line-2 bg-dp-surface px-4 py-3">{bottomAction}</div>
        ) : null}

        <nav className="grid flex-none grid-cols-4 gap-0.5 border-t border-dp-line-2 bg-dp-surface px-2 pt-2.5 pb-6.5">
          {MOBILE_TABS.map((tab) => {
            const active = tab.key === activeTab;
            return (
              <Link
                key={tab.key}
                href={tab.href}
                aria-current={active ? "page" : undefined}
                className="flex flex-col items-center gap-1.5 py-2"
              >
                <span className={`h-5 w-5 rounded-[5px] ${active ? "bg-dp-green" : "bg-dp-off"}`} aria-hidden />
                <span
                  className={`text-[11px] leading-none ${
                    active ? "font-semibold text-dp-green" : "font-medium text-dp-muted"
                  }`}
                >
                  {tab.label}
                </span>
              </Link>
            );
          })}
        </nav>
      </div>
    </div>
  );
}

/** 하단 고정 액션 바 — 되돌리기 : n건 적용 = 1 : 1.4 (핸드오프 Mobile) */
export function MobileApplyBar({ count }: { count: number }) {
  return (
    <div className="flex gap-2.5">
      <span className="flex-1 rounded-[9px] border border-dp-line-strong py-3.5 text-center text-[13.5px] leading-none font-semibold text-dp-body">
        되돌리기
      </span>
      <span className="flex-[1.4] rounded-[9px] bg-dp-green py-3.5 text-center text-[13.5px] leading-none font-semibold text-dp-on-green">
        {count}건 적용
      </span>
    </div>
  );
}
