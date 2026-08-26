"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { listFarms } from "@/lib/api/farms";
import { subscribeFarmsChanged } from "@/lib/farmsBus";
import type { FarmSummaryResponse } from "@/types";
import GlobalBar from "./GlobalBar";
import { computeActiveGroupKey, extractFarmIdFromPath } from "./nav-config";
import SideNav from "./SideNav";

// 운영 셸(이슈 #133, 1단계) — 시안 핸드오프 Global Structure 절을 그대로 적용한 최상위 골격.
// 구 DashboardHeader + Sidebar를 대체한다. (dashboard) 그룹 layout이 인증 가드를 통과시킨
// children을 감싼다.
export default function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname() ?? "";
  const router = useRouter();

  const [farms, setFarms] = useState<FarmSummaryResponse[] | null>(null);
  const [farmsLoadFailed, setFarmsLoadFailed] = useState(false);
  const [navCollapsed, setNavCollapsed] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  // pathname 변화에 맞춰 서랍을 닫는다 — "prop 변화에 상태를 리셋"하는 경우라 useEffect
  // 대신 렌더 중 조정 패턴을 쓴다(react-hooks/set-state-in-effect 회피, React 공식 권장 패턴).
  const [drawerClosedFor, setDrawerClosedFor] = useState(pathname);
  if (pathname !== drawerClosedFor) {
    setDrawerClosedFor(pathname);
    setDrawerOpen(false);
  }

  // 구 Sidebar와 동일한 로드/구독 패턴(이슈 #42 P2-1) — 농장 생성/합류/삭제 직후 재조회.
  useEffect(() => {
    let cancelled = false;

    function load() {
      listFarms()
        .then((data) => {
          if (!cancelled) {
            setFarms(data);
            setFarmsLoadFailed(false);
          }
        })
        .catch(() => {
          if (!cancelled) setFarmsLoadFailed(true);
        });
    }

    load();
    const unsubscribe = subscribeFarmsChanged(load);
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  const pathFarmId = extractFarmIdFromPath(pathname);
  const effectiveFarmId = pathFarmId ?? (farms && farms.length > 0 ? String(farms[0].id) : null);
  const activeGroupKey = computeActiveGroupKey(pathname, effectiveFarmId);

  // 농장 드롭다운/내 농장 스위처 — 현재 화면을 유지한 채 farmId만 교체(이슈 #133).
  // farm 스코프가 없는 화면(예: /dashboard)에서는 해당 농장 상세로 이동한다.
  function handleSelectFarm(nextFarmId: string) {
    if (pathFarmId) {
      router.push(pathname.replace(`/farms/${pathFarmId}`, `/farms/${nextFarmId}`));
    } else {
      router.push(`/farms/${nextFarmId}`);
    }
  }

  return (
    <div className="flex flex-1 flex-col">
      <GlobalBar
        farms={farms}
        farmsLoadFailed={farmsLoadFailed}
        effectiveFarmId={effectiveFarmId}
        activeGroupKey={activeGroupKey}
        onSelectFarm={handleSelectFarm}
        onOpenDrawer={() => setDrawerOpen(true)}
      />

      <div className="flex min-h-0 flex-1">
        {/* 기준(1920)·노트북(1440–1919): 좌측 고정 내비 */}
        {!navCollapsed ? (
          <SideNav
            className="hidden min-[1440px]:flex"
            farms={farms}
            farmsLoadFailed={farmsLoadFailed}
            effectiveFarmId={effectiveFarmId}
            pathname={pathname}
            activeGroupKey={activeGroupKey}
            onSelectFarm={handleSelectFarm}
            onCollapse={() => setNavCollapsed(true)}
          />
        ) : (
          <div className="hidden w-8 flex-none flex-col items-center border-r border-dp-line-2 bg-dp-surface py-3.5 min-[1440px]:flex">
            <button
              type="button"
              onClick={() => setNavCollapsed(false)}
              aria-label="메뉴 펼치기"
              className="flex h-6 w-6 items-center justify-center rounded-md border border-dp-line-strong text-[11px] text-dp-sub hover:bg-dp-inset"
            >
              »
            </button>
          </div>
        )}

        {/* 태블릿 이하(<1440): 서랍(오버레이) — 이번 단계는 구조만(핸드오프 §Responsive) */}
        {drawerOpen && (
          <div className="fixed inset-0 z-50 flex min-[1440px]:hidden">
            <button
              type="button"
              aria-label="메뉴 닫기"
              onClick={() => setDrawerOpen(false)}
              className="absolute inset-0 bg-black/40"
            />
            <div className="relative flex h-full w-[240px] max-w-[85vw] flex-col shadow-xl">
              <SideNav
                farms={farms}
                farmsLoadFailed={farmsLoadFailed}
                effectiveFarmId={effectiveFarmId}
                pathname={pathname}
                activeGroupKey={activeGroupKey}
                onSelectFarm={handleSelectFarm}
                onCollapse={() => setDrawerOpen(false)}
                onNavigate={() => setDrawerOpen(false)}
              />
            </div>
          </div>
        )}

        <main className="min-w-0 flex-1 bg-dp-canvas">{children}</main>
      </div>
    </div>
  );
}
