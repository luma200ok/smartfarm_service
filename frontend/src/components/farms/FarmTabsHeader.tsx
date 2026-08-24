"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { getFarm } from "@/lib/api/farms";
import type { FarmResponse } from "@/types";

const CROP_LABELS: Record<string, string> = { TOMATO: "토마토" };

interface FarmTabsHeaderProps {
  farmId: string;
}

interface TabItem {
  href: string;
  label: string;
  isActive: (pathname: string) => boolean;
}

function buildTabs(farmId: string): TabItem[] {
  const base = `/farms/${farmId}`;
  return [
    { href: base, label: "개요", isActive: (pathname) => pathname === base },
    {
      href: `${base}/diagnoses`,
      label: "진단",
      isActive: (pathname) => pathname === `${base}/diagnoses` || pathname.startsWith(`${base}/diagnoses/`),
    },
    {
      href: `${base}/prescriptions`,
      label: "처방",
      isActive: (pathname) =>
        pathname === `${base}/prescriptions` || pathname.startsWith(`${base}/prescriptions/`),
    },
    {
      href: `${base}/logs`,
      label: "작업일지",
      isActive: (pathname) => pathname === `${base}/logs` || pathname.startsWith(`${base}/logs/`),
    },
    {
      href: `${base}/nutrient`,
      label: "양액",
      isActive: (pathname) => pathname === `${base}/nutrient` || pathname.startsWith(`${base}/nutrient/`),
    },
    {
      href: `${base}/data`,
      label: "데이터",
      isActive: (pathname) => pathname === `${base}/data`,
    },
    {
      href: `${base}/devices`,
      label: "장비",
      isActive: (pathname) => pathname === `${base}/devices`,
    },
    {
      href: `${base}/control`,
      label: "제어",
      isActive: (pathname) => pathname === `${base}/control`,
    },
    { href: `${base}/members`, label: "멤버", isActive: (pathname) => pathname === `${base}/members` },
    {
      href: `${base}/chat`,
      label: "AI 상담",
      isActive: (pathname) => pathname === `${base}/chat` || pathname.startsWith(`${base}/chat/`),
    },
  ];
}

// 농장 상세 하위(개요·진단·처방·멤버) 전용 컨텍스트 헤더 — 이슈 #43.
// 탭 전환 중에도 농장명+탭바가 유지되도록 (dashboard)/farms/[farmId]/layout.tsx에 상주한다.
// 농장명 조회는 이 헤더에서 1회 수행하고, 각 탭 페이지는 자신에게 필요한 데이터를 독립적으로
// 조회한다(전역 상태/캐시 도입 금지 — 과설계 금지 지침).
export default function FarmTabsHeader({ farmId }: FarmTabsHeaderProps) {
  const pathname = usePathname() ?? "";
  const [farm, setFarm] = useState<FarmResponse | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setFarm(null);
      setLoadFailed(false);
      try {
        const data = await getFarm(farmId);
        if (!cancelled) setFarm(data);
      } catch {
        // 헤더는 보조 표시 영역이라 에러를 요란하게 띄우지 않고 조용히 자리만 비운다.
        // 실제 접근 권한/404 처리는 각 탭 페이지(예: 개요의 FarmOverview)가 담당한다.
        if (!cancelled) setLoadFailed(true);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [farmId]);

  const tabs = buildTabs(farmId);

  return (
    <div className="flex flex-col gap-3 border-b border-zinc-200 px-6 py-4 dark:border-zinc-800">
      <div className="flex items-center gap-2 min-h-6">
        {farm && (
          <>
            <h1 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">{farm.name}</h1>
            <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
              {CROP_LABELS[farm.cropType] ?? farm.cropType}
            </span>
          </>
        )}
        {!farm && loadFailed && (
          <p className="text-sm text-zinc-400 dark:text-zinc-500">농장 정보를 불러오지 못했습니다.</p>
        )}
      </div>
      <nav className="flex gap-4 text-sm">
        {tabs.map((tab) => {
          const active = tab.isActive(pathname);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={active ? "page" : undefined}
              className={`border-b-2 pb-2 transition-colors ${
                active
                  ? "border-zinc-900 font-semibold text-zinc-900 dark:border-zinc-50 dark:text-zinc-50"
                  : "border-transparent text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
              }`}
            >
              {tab.label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
