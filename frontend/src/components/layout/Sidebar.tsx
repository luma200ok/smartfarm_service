"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import LogoutButton from "@/components/auth/LogoutButton";
import ThemeToggle from "@/components/ui/ThemeToggle";
import { listFarms } from "@/lib/api/farms";
import type { FarmSummaryResponse } from "@/types";
import { NAV_ITEMS } from "./DashboardHeader";

// 좌측 고정 사이드바(이슈 #42) — 데스크톱(lg 이상) 전용 상시 노출 내비 + 내 농장 스위처.
// 모바일(<lg)에서는 숨기고 DashboardHeader의 상단 내비가 그 역할을 대신한다.
// 농장 목록은 페이지 이동 시 리마운트로 재조회되는 수준이면 충분(전역 상태/캐시 도입 금지 — 핸드오프 지침).
export default function Sidebar() {
  const pathname = usePathname() ?? "";
  const [farms, setFarms] = useState<FarmSummaryResponse[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listFarms()
      .then((data) => {
        if (!cancelled) setFarms(data);
      })
      .catch(() => {
        // 사이드바는 보조 내비게이션이라 에러를 요란하게 띄우지 않고 조용히 자리만 비운다.
        if (!cancelled) setLoadFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <aside className="sticky top-0 hidden h-screen w-64 flex-shrink-0 flex-col overflow-y-auto border-r border-zinc-200 dark:border-zinc-800 lg:flex">
      <div className="flex flex-col gap-6 px-4 py-6">
        <Link href="/dashboard" className="px-2 text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          스마트팜
        </Link>

        <nav className="flex flex-col gap-1 text-sm">
          {NAV_ITEMS.map((item) => {
            const active = item.isActive(pathname);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-md px-2 py-1.5 transition-colors ${
                  active
                    ? "bg-zinc-100 font-semibold text-zinc-900 dark:bg-zinc-800 dark:text-zinc-50"
                    : "text-zinc-500 hover:bg-zinc-50 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-900 dark:hover:text-zinc-50"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="flex flex-col gap-2">
          <h2 className="px-2 text-xs font-semibold uppercase tracking-wide text-zinc-400 dark:text-zinc-500">
            내 농장
          </h2>
          {farms === null && !loadFailed && (
            <p className="px-2 text-xs text-zinc-400 dark:text-zinc-500">불러오는 중...</p>
          )}
          {loadFailed && (
            <p className="px-2 text-xs text-zinc-400 dark:text-zinc-500">
              농장 목록을 불러오지 못했습니다.
            </p>
          )}
          {farms !== null && farms.length === 0 && !loadFailed && (
            <p className="px-2 text-xs text-zinc-400 dark:text-zinc-500">등록된 농장이 없습니다.</p>
          )}
          {farms !== null && farms.length > 0 && (
            <ul className="flex flex-col gap-0.5">
              {farms.map((farm) => {
                const active =
                  pathname === `/farms/${farm.id}` || pathname.startsWith(`/farms/${farm.id}/`);
                return (
                  <li key={farm.id}>
                    <Link
                      href={`/farms/${farm.id}`}
                      aria-current={active ? "page" : undefined}
                      className={`block truncate rounded-md px-2 py-1.5 text-sm transition-colors ${
                        active
                          ? "bg-zinc-100 font-semibold text-zinc-900 dark:bg-zinc-800 dark:text-zinc-50"
                          : "text-zinc-500 hover:bg-zinc-50 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-900 dark:hover:text-zinc-50"
                      }`}
                    >
                      {farm.name}
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>

      <div className="mt-auto flex items-center justify-between gap-2 border-t border-zinc-200 px-4 py-4 dark:border-zinc-800">
        <ThemeToggle />
        <LogoutButton />
      </div>
    </aside>
  );
}
