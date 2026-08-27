"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { getAlarmStats, getUnacknowledgedCount } from "@/lib/api/alarms";
import { listFarms } from "@/lib/api/farms";
import { listSavedAnalyses } from "@/lib/api/savedAnalyses";
import { subscribeAlarmsChanged } from "@/lib/alarmsBus";
import { subscribeFarmsChanged } from "@/lib/farmsBus";
import { subscribeSavedAnalysesChanged } from "@/lib/savedAnalysesBus";
import type {
  AlarmStatsResponse,
  FarmSummaryResponse,
  SavedAnalysisResponse,
} from "@/types";
import { AppShellContextProvider } from "./AppShellContext";
import GlobalBar from "./GlobalBar";
import MobileTabBar, { type MobileTabKey } from "./MobileTabBar";
import MobileTopBar, { type MobileTopBarVariant } from "./MobileTopBar";
import {
  computeActiveGroupKey,
  extractFarmIdFromPath,
  NAV_GROUPS,
  resolveGroupHref,
  resolveMobileTitle,
} from "./nav-config";
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
  const effectiveFarmId =
    pathFarmId ?? (farms && farms.length > 0 ? String(farms[0].id) : null);
  const activeGroupKey = computeActiveGroupKey(pathname, effectiveFarmId);

  // TopBar 알람 배지(이슈 #136) — 농장이 바뀌거나 이 화면에서 알람을 확인/처리하면(alarmsBus)
  // 다시 조회한다. farmsBus의 load/subscribe 패턴과 동일.
  const [unackCount, setUnackCount] = useState<number | null>(null);
  const [unackCountFailed, setUnackCountFailed] = useState(false);

  // effectiveFarmId가 없어지면(예: 농장 목록 자체가 빈 상태) 배지를 비운다 — drawerClosedFor와
  // 동일한 "렌더 중 조정" 패턴(react-hooks/set-state-in-effect 회피).
  const [unackFarmId, setUnackFarmId] = useState(effectiveFarmId);
  if (effectiveFarmId !== unackFarmId) {
    setUnackFarmId(effectiveFarmId);
    if (!effectiveFarmId) {
      setUnackCount(null);
      setUnackCountFailed(false);
    }
  }

  useEffect(() => {
    if (!effectiveFarmId) return;
    let cancelled = false;
    function load() {
      getUnacknowledgedCount(effectiveFarmId!)
        .then((res) => {
          if (!cancelled) {
            setUnackCount(res.count);
            setUnackCountFailed(false);
          }
        })
        .catch(() => {
          if (!cancelled) setUnackCountFailed(true);
        });
    }
    load();
    const unsubscribe = subscribeAlarmsChanged(load);
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [effectiveFarmId]);

  // 좌측 내비 하단 "최근 7일" 통계(이슈 #136) — 알람 현황 화면에 있을 때만 조회한다
  // (핸드오프: 하단 정보 블록을 "내 농장" 대신 이 통계로 교체).
  const [alarmStats, setAlarmStats] = useState<AlarmStatsResponse | null>(null);
  const [alarmStatsFailed, setAlarmStatsFailed] = useState(false);

  const showAlarmStats = activeGroupKey === "alarm" && effectiveFarmId !== null;
  const [wasShowingAlarmStats, setWasShowingAlarmStats] =
    useState(showAlarmStats);
  if (showAlarmStats !== wasShowingAlarmStats) {
    setWasShowingAlarmStats(showAlarmStats);
    if (!showAlarmStats) {
      setAlarmStats(null);
      setAlarmStatsFailed(false);
    }
  }

  useEffect(() => {
    if (!showAlarmStats) return;
    let cancelled = false;
    function load() {
      getAlarmStats(effectiveFarmId!, 7)
        .then((data) => {
          if (!cancelled) {
            setAlarmStats(data);
            setAlarmStatsFailed(false);
          }
        })
        .catch(() => {
          if (!cancelled) setAlarmStatsFailed(true);
        });
    }
    load();
    const unsubscribe = subscribeAlarmsChanged(load);
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [showAlarmStats, effectiveFarmId]);

  // 좌측 내비 하단 "저장한 분석" 목록(이슈 #144) — 데이터 화면에 있을 때만 조회한다
  // (alarmStats와 동일 패턴 — 핸드오프: 하단 정보 블록을 "내 농장" 대신 이 목록으로 교체).
  const [savedAnalyses, setSavedAnalyses] = useState<
    SavedAnalysisResponse[] | null
  >(null);
  const [savedAnalysesFailed, setSavedAnalysesFailed] = useState(false);

  const showSavedAnalyses =
    activeGroupKey === "data" && effectiveFarmId !== null;
  const [wasShowingSavedAnalyses, setWasShowingSavedAnalyses] =
    useState(showSavedAnalyses);
  if (showSavedAnalyses !== wasShowingSavedAnalyses) {
    setWasShowingSavedAnalyses(showSavedAnalyses);
    if (!showSavedAnalyses) {
      setSavedAnalyses(null);
      setSavedAnalysesFailed(false);
    }
  }

  useEffect(() => {
    if (!showSavedAnalyses) return;
    let cancelled = false;
    function load() {
      listSavedAnalyses(effectiveFarmId!)
        .then((data) => {
          if (!cancelled) {
            setSavedAnalyses(data);
            setSavedAnalysesFailed(false);
          }
        })
        .catch(() => {
          if (!cancelled) setSavedAnalysesFailed(true);
        });
    }
    load();
    const unsubscribe = subscribeSavedAnalysesChanged(load);
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [showSavedAnalyses, effectiveFarmId]);

  // 농장 드롭다운/내 농장 스위처 — 현재 화면을 유지한 채 farmId만 교체(이슈 #133).
  // farm 스코프가 없는 화면(예: /dashboard)에서는 해당 농장 상세로 이동한다.
  function handleSelectFarm(nextFarmId: string) {
    if (pathFarmId) {
      router.push(
        pathname.replace(`/farms/${pathFarmId}`, `/farms/${nextFarmId}`),
      );
    } else {
      router.push(`/farms/${nextFarmId}`);
    }
  }

  // 모바일 셸(이슈 #147) — 하단 탭 4개 + 상단 다크 바. 탭 href는 데스크톱 GlobalBar와 같은
  // NAV_GROUPS/resolveGroupHref를 재사용한다(제어·알람은 그대로, 홈만 예외: "대시보드" 그룹은
  // static 리프가 앞이라 항상 /dashboard로 풀리는데, 모바일 홈은 시안상 "현재 농장 개요"라
  // 농장이 있으면 /farms/{id}로 직접 보낸다 — 농장 전환 블록을 탭에서 곧장 눌러 들어가는 화면과
  // 일치시키기 위함).
  const controlGroup = NAV_GROUPS.find((g) => g.key === "control")!;
  const alarmGroup = NAV_GROUPS.find((g) => g.key === "alarm")!;
  const mobileHomeHref = effectiveFarmId
    ? `/farms/${effectiveFarmId}`
    : "/dashboard";
  const mobileControlHref = resolveGroupHref(controlGroup, effectiveFarmId);
  const mobileAlarmHref = resolveGroupHref(alarmGroup, effectiveFarmId);
  const mobileTab: MobileTabKey =
    activeGroupKey === "control"
      ? "control"
      : activeGroupKey === "alarm"
        ? "alarm"
        : activeGroupKey === "dashboard"
          ? "home"
          : "more";
  const mobileVariant: MobileTopBarVariant =
    mobileTab === "home" ? "home" : pathname === "/more" ? "more" : "sub";
  const mobileTitle = resolveMobileTitle(pathname, effectiveFarmId);

  return (
    <AppShellContextProvider
      value={{ farms, farmsLoadFailed, effectiveFarmId }}
    >
      <div className="flex flex-1 flex-col">
        {/* 데스크톱·태블릿(≥768px) 상단 바 — 모바일에서는 완전히 대체되므로 숨긴다(회귀 금지). */}
        <div className="hidden min-[768px]:contents">
          <GlobalBar
            farms={farms}
            farmsLoadFailed={farmsLoadFailed}
            effectiveFarmId={effectiveFarmId}
            activeGroupKey={activeGroupKey}
            unackCount={unackCount}
            unackCountFailed={unackCountFailed}
            onSelectFarm={handleSelectFarm}
            onOpenDrawer={() => setDrawerOpen(true)}
          />
        </div>

        {/* 모바일 전용(<768px) 상단 다크 바(이슈 #147) */}
        <MobileTopBar
          className="min-[768px]:hidden"
          variant={mobileVariant}
          farms={farms}
          farmsLoadFailed={farmsLoadFailed}
          effectiveFarmId={effectiveFarmId}
          unackCount={unackCount}
          onSelectFarm={handleSelectFarm}
          title={mobileTitle}
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
              alarmStats={alarmStats}
              alarmStatsFailed={alarmStatsFailed}
              savedAnalyses={savedAnalyses}
              savedAnalysesFailed={savedAnalysesFailed}
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
                  alarmStats={alarmStats}
                  alarmStatsFailed={alarmStatsFailed}
                  savedAnalyses={savedAnalyses}
                  savedAnalysesFailed={savedAnalysesFailed}
                  onSelectFarm={handleSelectFarm}
                  onCollapse={() => setDrawerOpen(false)}
                  onNavigate={() => setDrawerOpen(false)}
                />
              </div>
            </div>
          )}

          <main className="min-w-0 flex-1 bg-dp-canvas">{children}</main>
        </div>

        {/* 모바일 전용(<768px) 하단 탭 4개(이슈 #147) */}
        <MobileTabBar
          className="min-[768px]:hidden"
          active={mobileTab}
          homeHref={mobileHomeHref}
          controlHref={mobileControlHref}
          alarmHref={mobileAlarmHref}
        />
      </div>
    </AppShellContextProvider>
  );
}
