"use client";

import Link from "next/link";
import { useState } from "react";
import type { FarmSummaryResponse } from "@/types";
import { NAV_GROUPS, getLeafHref, isLeafActive } from "./nav-config";

interface SideNavProps {
  farms: FarmSummaryResponse[] | null;
  farmsLoadFailed: boolean;
  effectiveFarmId: string | null;
  pathname: string;
  activeGroupKey: string | null;
  onSelectFarm: (farmId: string) => void;
  /** « (데스크톱 접기) 또는 서랍 닫기 — variant에 따라 의미가 다르다. */
  onCollapse: () => void;
  /** 서랍(변형)에서만: 항목 클릭 후 서랍을 닫는다. */
  onNavigate?: () => void;
  className?: string;
}

// 좌측 내비(240px, 노트북 208px) 아코디언 — 핸드오프 Global Structure 절.
// 한 번에 한 그룹만 펼치고, 펼침 상태는 activeGroupKey(= 현재 pathname에서 파생)와 동기화된다.
// 그룹 헤더 클릭은 순수 UI 토글(네비게이션 없음) — 다른 대분류를 상단 탭 없이도 미리 볼 수 있게.
export default function SideNav({
  farms,
  farmsLoadFailed,
  effectiveFarmId,
  pathname,
  activeGroupKey,
  onSelectFarm,
  onCollapse,
  onNavigate,
  className = "",
}: SideNavProps) {
  const [expandedKey, setExpandedKey] = useState<string>(activeGroupKey ?? NAV_GROUPS[0].key);

  // 상단 탭 클릭 등 pathname 변화로 그룹이 바뀌면 좌측 내비도 같은 그룹을 펼친다(상태 공유).
  // "prop 변화에 상태를 동기화"하는 경우라 useEffect 대신 렌더 중 조정 패턴을 쓴다
  // (react-hooks/set-state-in-effect 회피, React 공식 권장 패턴 — AppShell의 drawerOpen과 동일).
  const [syncedGroupKey, setSyncedGroupKey] = useState(activeGroupKey);
  if (activeGroupKey !== syncedGroupKey) {
    setSyncedGroupKey(activeGroupKey);
    if (activeGroupKey) setExpandedKey(activeGroupKey);
  }

  return (
    <aside
      className={`flex w-[208px] flex-none flex-col overflow-hidden border-r border-dp-line-2 bg-dp-surface min-[1920px]:w-[240px] ${className}`}
    >
      <div className="flex flex-none items-center gap-2 px-3.5 pt-3.5 pb-2.5">
        <span className="flex-1 font-mono text-[10.5px] font-semibold tracking-[0.06em] text-dp-muted">
          전체 메뉴
        </span>
        <button
          type="button"
          onClick={onCollapse}
          aria-label="메뉴 접기"
          className="flex h-6 w-6 flex-none items-center justify-center rounded-md border border-dp-line-strong text-[11px] text-dp-sub hover:bg-dp-inset"
        >
          «
        </button>
      </div>

      <nav className="flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto px-2.5 pb-2">
        {NAV_GROUPS.map((group) => {
          const expanded = group.key === expandedKey;
          return (
            <div key={group.key}>
              <button
                type="button"
                onClick={() => setExpandedKey(group.key)}
                aria-expanded={expanded}
                className={`flex w-full items-center gap-2 rounded-[7px] px-3 py-2.5 text-left transition-colors ${
                  expanded ? "bg-dp-inset" : "hover:bg-dp-inset-alt"
                }`}
              >
                <span
                  className={`font-mono text-[9.5px] font-semibold ${expanded ? "text-dp-green" : "text-dp-faint"}`}
                >
                  {group.no}
                </span>
                <span className={`flex-1 text-[13px] font-semibold ${expanded ? "text-dp-ink" : "text-dp-body"}`}>
                  {group.label}
                </span>
                <span className="text-[10px] text-dp-faint">{expanded ? "▾" : "›"}</span>
              </button>

              {expanded && (
                <div className="flex flex-col gap-0.5 py-1">
                  {group.items.map((item, i) => {
                    const href = getLeafHref(item, effectiveFarmId);
                    const active = isLeafActive(item, pathname, effectiveFarmId);

                    if (!href) {
                      return (
                        <div
                          key={i}
                          aria-disabled="true"
                          className="ml-2.5 flex items-center gap-2 rounded-[7px] px-3 py-2.5 opacity-45"
                        >
                          <span className="h-[3px] w-[3px] flex-none rounded-full bg-dp-faint" />
                          <span className="flex-1 text-[12.5px] text-dp-sub">{item.label}</span>
                        </div>
                      );
                    }

                    return (
                      <Link
                        key={i}
                        href={href}
                        onClick={onNavigate}
                        aria-current={active ? "page" : undefined}
                        className={`ml-2.5 flex items-center gap-2 rounded-[7px] px-3 py-2.5 transition-colors ${
                          active
                            ? "border border-dp-green-line bg-dp-green-tint font-semibold text-dp-green-ink"
                            : "text-dp-body hover:bg-dp-inset-alt"
                        }`}
                      >
                        <span
                          className={`h-[3px] w-[3px] flex-none rounded-full ${active ? "bg-dp-green" : "bg-dp-faint"}`}
                        />
                        <span className="flex-1 text-[12.5px]">{item.label}</span>
                      </Link>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </nav>

      {/* 하단 정보 블록(바닥 고정) — 구 Sidebar의 "내 농장" 스위처를 여기로 옮겼다(§ 특히 주의 1). */}
      <div className="mx-2.5 mb-3.5 flex-none rounded-lg bg-dp-inset p-3">
        <div className="mb-2 font-mono text-[10.5px] font-semibold tracking-[0.05em] text-dp-muted">내 농장</div>
        {farms === null && !farmsLoadFailed && <p className="text-xs text-dp-faint">불러오는 중...</p>}
        {farmsLoadFailed && <p className="text-xs text-dp-faint">목록을 불러오지 못했습니다.</p>}
        {farms !== null && farms.length === 0 && !farmsLoadFailed && (
          <p className="text-xs text-dp-faint">등록된 농장이 없습니다.</p>
        )}
        {farms !== null && farms.length > 0 && (
          <ul className="flex flex-col gap-1.5">
            {farms.map((farm) => {
              const active = String(farm.id) === effectiveFarmId;
              return (
                <li key={farm.id}>
                  <button
                    type="button"
                    onClick={() => {
                      onSelectFarm(String(farm.id));
                      onNavigate?.();
                    }}
                    aria-current={active ? "true" : undefined}
                    className={`flex w-full items-center gap-1.5 truncate text-left text-[12px] font-medium ${
                      active ? "text-dp-ink" : "text-dp-body hover:text-dp-ink"
                    }`}
                  >
                    <span className="h-1.5 w-1.5 flex-none rounded-full bg-dp-green" />
                    <span className="truncate">{farm.name}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </aside>
  );
}
