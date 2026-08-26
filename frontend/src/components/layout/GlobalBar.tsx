"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState, useSyncExternalStore } from "react";
import ProfileMenu from "@/components/auth/ProfileMenu";
import type { FarmSummaryResponse } from "@/types";
import { NAV_GROUPS, resolveGroupHref } from "./nav-config";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function formatNow(date: Date): string {
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const min = String(date.getMinutes()).padStart(2, "0");
  return `${yyyy}. ${mm}. ${dd} (${WEEKDAYS[date.getDay()]}) ${hh}:${min}`;
}

// 분 단위로 갱신되는 현재 시각 — ThemeToggle(src/components/ui/ThemeToggle.tsx)과 동일하게
// useEffect+setState 대신 useSyncExternalStore로 외부 시스템(시계)에 동기화한다
// (react-hooks/set-state-in-effect 회피). 서버 렌더는 항상 null이라가 마운트 후 채워진다.
function subscribeClock(callback: () => void) {
  const id = setInterval(callback, 60_000);
  return () => clearInterval(id);
}
function getClockSnapshot(): string {
  return formatNow(new Date());
}
function getClockServerSnapshot(): string | null {
  return null;
}

interface GlobalBarProps {
  farms: FarmSummaryResponse[] | null;
  farmsLoadFailed: boolean;
  effectiveFarmId: string | null;
  activeGroupKey: string | null;
  onSelectFarm: (farmId: string) => void;
  onOpenDrawer: () => void;
}

// 상단 글로벌 바(56px 고정) — 핸드오프 Global Structure 절. 로고 · 농장 드롭다운 · 대분류 탭 6개 ·
// 우측(현재 시각 · 알람 배지 자리 · AI 챗봇 · 아바타/프로필 메뉴).
export default function GlobalBar({
  farms,
  farmsLoadFailed,
  effectiveFarmId,
  activeGroupKey,
  onSelectFarm,
  onOpenDrawer,
}: GlobalBarProps) {
  const now = useSyncExternalStore(subscribeClock, getClockSnapshot, getClockServerSnapshot);

  return (
    <header className="sticky top-0 z-40 flex h-14 flex-none items-center gap-3.5 bg-dp-bar px-4 text-white min-[1440px]:px-5">
      <button
        type="button"
        onClick={onOpenDrawer}
        aria-label="메뉴 열기"
        className="flex h-8 w-8 flex-none items-center justify-center rounded-md text-white/80 hover:bg-white/10 min-[1440px]:hidden"
      >
        ☰
      </button>

      <Link href="/dashboard" className="flex-none text-[15px] leading-none font-bold tracking-[-0.02em]">
        스마트팜 <span className="text-[#5fd08c]">DFX</span>
      </Link>

      <div className="hidden h-4 w-px flex-none bg-white/[0.18] min-[640px]:block" />

      <FarmDropdown
        farms={farms}
        farmsLoadFailed={farmsLoadFailed}
        effectiveFarmId={effectiveFarmId}
        onSelectFarm={onSelectFarm}
      />

      <nav className="ml-1 flex min-w-0 flex-1 gap-1 overflow-x-auto">
        {NAV_GROUPS.map((group) => {
          const active = group.key === activeGroupKey;
          const href = resolveGroupHref(group, effectiveFarmId);
          const className = `rounded-md px-[13px] py-[7px] text-[12.5px] leading-none whitespace-nowrap transition-colors ${
            active ? "bg-white/[0.14] font-semibold text-white" : "text-white/65 hover:text-white/90"
          }`;
          if (!href) {
            return (
              <span key={group.key} aria-disabled="true" className={`${className} cursor-not-allowed opacity-50`}>
                {group.label}
              </span>
            );
          }
          return (
            <Link key={group.key} href={href} aria-current={active ? "page" : undefined} className={className}>
              {group.label}
            </Link>
          );
        })}
      </nav>

      <span className="hidden flex-none text-[12px] leading-none text-white/70 min-[1024px]:inline">
        {now}
      </span>

      {/* 알람 배지 — 2단계에서 실 데이터·화면 연결 전까지는 자리만 잡는다(클릭 비활성). */}
      <span
        aria-disabled="true"
        className="hidden flex-none items-center gap-1.5 text-[12px] leading-none text-white/40 min-[1024px]:flex"
      >
        알람
        <span className="rounded-[9px] bg-white/10 px-[7px] py-0.5 font-mono text-[10px] leading-[1.4] font-semibold text-white/50">
          –
        </span>
      </span>

      <Link
        href={effectiveFarmId ? `/farms/${effectiveFarmId}/chat` : "#"}
        aria-disabled={!effectiveFarmId}
        className={`hidden flex-none rounded-md border border-white/25 px-3 py-1.5 text-[12px] leading-none min-[768px]:inline ${
          effectiveFarmId ? "hover:bg-white/10" : "pointer-events-none opacity-40"
        }`}
      >
        AI 챗봇
      </Link>

      <ProfileMenu />
    </header>
  );
}

interface FarmDropdownProps {
  farms: FarmSummaryResponse[] | null;
  farmsLoadFailed: boolean;
  effectiveFarmId: string | null;
  onSelectFarm: (farmId: string) => void;
}

// 농장 드롭다운 — listFarms() 재사용(새 API 없음). ProfileMenu와 동일한 disclosure 패턴.
function FarmDropdown({ farms, farmsLoadFailed, effectiveFarmId, onSelectFarm }: FarmDropdownProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const panelId = useId();

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  const currentFarm = farms?.find((f) => String(f.id) === effectiveFarmId) ?? null;
  const label = farmsLoadFailed
    ? "농장 목록 오류"
    : farms === null
      ? "불러오는 중..."
      : currentFarm
        ? currentFarm.name
        : farms.length === 0
          ? "농장 없음"
          : "농장 선택";

  return (
    <div ref={containerRef} className="relative hidden flex-none min-[640px]:block">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((prev) => !prev)}
        className="flex max-w-[180px] items-center gap-2 rounded-[7px] bg-white/10 px-3 py-1.5 text-[12.5px] leading-none font-medium hover:bg-white/[0.16]"
      >
        <span className="truncate">{label}</span>
        <span className="opacity-50">▾</span>
      </button>

      {open && (
        <div
          id={panelId}
          className="absolute left-0 z-20 mt-2 w-64 rounded-lg border border-dp-line bg-dp-surface p-1 text-dp-body shadow-lg"
        >
          {farms !== null && farms.length > 0 && (
            <ul className="flex flex-col gap-0.5 p-1">
              {farms.map((farm) => {
                const active = String(farm.id) === effectiveFarmId;
                return (
                  <li key={farm.id}>
                    <button
                      type="button"
                      onClick={() => {
                        onSelectFarm(String(farm.id));
                        setOpen(false);
                      }}
                      aria-current={active ? "true" : undefined}
                      className={`flex w-full items-center rounded-md px-3 py-2 text-left text-sm ${
                        active ? "bg-dp-green-tint font-semibold text-dp-green-ink" : "hover:bg-dp-inset"
                      }`}
                    >
                      {farm.name}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          {farms !== null && farms.length === 0 && !farmsLoadFailed && (
            <p className="px-3 py-2 text-sm text-dp-faint">등록된 농장이 없습니다.</p>
          )}
          {farmsLoadFailed && <p className="px-3 py-2 text-sm text-dp-faint">농장 목록을 불러오지 못했습니다.</p>}
          <div className="my-1 border-t border-dp-line" />
          <Link
            href="/farms"
            onClick={() => setOpen(false)}
            className="block rounded-md px-3 py-2 text-sm font-medium text-dp-body hover:bg-dp-inset"
          >
            농장 목록 전체 보기
          </Link>
        </div>
      )}
    </div>
  );
}
